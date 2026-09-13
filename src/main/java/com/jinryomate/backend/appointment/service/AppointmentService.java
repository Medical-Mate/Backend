package com.jinryomate.backend.appointment.service;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.UpdateAppointmentRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.TodoRequest;
import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.appointment.entity.AppointmentTodo;
import com.jinryomate.backend.appointment.repository.AppointmentRepository;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    /**
     * 월·일자 경계를 자를 기준 시간대.
     *
     * <p>"9월 12일의 일정"은 사용자가 사는 곳의 9월 12일이다. UTC 로 자르면
     * 한국 시각 오전 9시 이전 일정이 전날로 밀린다. MVP 대상이 국내라 KST 로 고정한다.
     * 해외 사용자가 생기면 앱이 시간대를 보내도록 바꾼다.
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final BriefingCardService briefingCardService;

    /** 캘린더 월 뷰 (점 표시용). */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByMonth(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return listBetween(userId, ym.atDay(1), ym.atEndOfMonth());
    }

    /** 캘린더 일자별. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByDate(Long userId, LocalDate date) {
        return listBetween(userId, date, date);
    }

    /**
     * 홈의 "다가오는 일정".
     *
     * <p>아직 안 지났고 취소되지 않은 것만. 홈은 보통 하나만 쓰지만 목록으로 돌려준다 —
     * 같은 날 둘이 잡힐 수 있고, 앱이 몇 개를 보여줄지는 화면이 정한다.
     *
     * <p><b>오늘은 포함한다.</b> 날짜로 견주므로 오전에 잡힌 진료도 그날 하루는 남는다.
     * 시각까지 견주면 오전 10시 진료가 10시 1분에 목록에서 사라진다.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listUpcoming(Long userId) {
        return appointmentRepository
                .findUpcoming(userId, Appointment.Status.SCHEDULED, LocalDate.now(ZONE))
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional
    public AppointmentResponse create(Long userId, CreateAppointmentRequest request) {
        Appointment appointment = Appointment.of(
                userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.")),
                request.clinicName(),
                request.scheduledOn());
        appointment.applyDetails(request.department(), request.purpose(), request.origin());
        appointment.applySchedule(request.scheduledOn(), request.scheduledTime(), false);
        appointment.applyCards(findCards(userId, request.cardIds()));
        appointment.applyTodos(toTodos(request.todos()));

        appointmentRepository.save(appointment);

        // 병원명은 어느 병원에 다니는지가 드러나므로 로그에 남기지 않는다.
        log.info("일정 등록 userId={} appointmentId={}", userId, appointment.getId());
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse update(Long userId, Long appointmentId,
                                      UpdateAppointmentRequest request) {
        Appointment appointment = findOwned(userId, appointmentId);
        appointment.update(request.clinicName(), request.department(),
                request.purpose(), request.status());
        appointment.applySchedule(
                request.scheduledOn(), request.scheduledTime(), request.clearTime());
        appointment.applyCards(findCards(userId, request.cardIds()));
        appointment.applyTodos(toTodos(request.todos()));

        log.info("일정 수정 userId={} appointmentId={}", userId, appointmentId);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public void delete(Long userId, Long appointmentId) {
        appointmentRepository.delete(findOwned(userId, appointmentId));
        log.info("일정 삭제 userId={} appointmentId={}", userId, appointmentId);
    }

    // ---------- 내부 ----------

    private List<AppointmentResponse> listBetween(Long userId, LocalDate from, LocalDate to) {
        return appointmentRepository.findInRange(userId, from, to).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /**
     * 요청의 할 일을 저장할 값으로 옮긴다.
     *
     * <p>{@code null} 이면 {@code null} 을 그대로 돌려준다 — 엔티티가 그걸 "안 바꿈"으로
     * 읽는다. 빈 목록은 "전부 지움"이라 구별해야 한다.
     */
    private List<AppointmentTodo> toTodos(List<TodoRequest> requested) {
        if (requested == null) {
            return null;
        }
        return requested.stream()
                .map(t -> new AppointmentTodo(t.text(), t.done()))
                .toList();
    }

    /**
     * 붙일 카드를 찾는다. <b>남의 카드는 붙일 수 없다</b> — findOwned 가 소유를 검사한다.
     *
     * <p>{@code null} 이면 {@code null} 을 그대로 돌려준다. 엔티티가 그걸 "안 바꿈"으로
     * 읽고, 빈 목록은 "전부 뗌"이라 구별해야 한다.
     */
    private List<BriefingCard> findCards(Long userId, List<Long> cardIds) {
        if (cardIds == null) {
            return null;
        }
        return cardIds.stream()
                .distinct()
                .map(id -> briefingCardService.findOwned(userId, id))
                .toList();
    }

    /** 남의 일정은 존재 자체를 알려주지 않는다. */
    private Appointment findOwned(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (!appointment.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        return appointment;
    }
}
