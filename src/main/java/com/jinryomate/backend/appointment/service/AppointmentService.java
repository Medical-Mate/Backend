package com.jinryomate.backend.appointment.service;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.UpdateAppointmentRequest;
import com.jinryomate.backend.appointment.entity.Appointment;
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
        return listBetween(userId,
                ym.atDay(1).atStartOfDay(ZONE).toInstant(),
                ym.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant());
    }

    /** 캘린더 일자별. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByDate(Long userId, LocalDate date) {
        return listBetween(userId,
                date.atStartOfDay(ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    /**
     * 홈의 "다가오는 일정".
     *
     * <p>아직 안 지났고 취소되지 않은 것만. 홈은 보통 하나만 쓰지만 목록으로 돌려준다 —
     * 같은 날 둘이 잡힐 수 있고, 앱이 몇 개를 보여줄지는 화면이 정한다.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listUpcoming(Long userId) {
        return appointmentRepository
                .findAllByUserIdAndStatusAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
                        userId, Appointment.Status.SCHEDULED, Instant.now())
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
                request.scheduledAt());
        appointment.applyDetails(
                request.department(), request.purpose(), findCard(userId, request.cardId()));

        appointmentRepository.save(appointment);

        // 병원명은 어느 병원에 다니는지가 드러나므로 로그에 남기지 않는다.
        log.info("일정 등록 userId={} appointmentId={}", userId, appointment.getId());
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse update(Long userId, Long appointmentId,
                                      UpdateAppointmentRequest request) {
        Appointment appointment = findOwned(userId, appointmentId);
        appointment.update(
                request.clinicName(),
                request.department(),
                request.purpose(),
                request.scheduledAt(),
                request.status(),
                findCard(userId, request.cardId()),
                request.clearCard());

        log.info("일정 수정 userId={} appointmentId={}", userId, appointmentId);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public void delete(Long userId, Long appointmentId) {
        appointmentRepository.delete(findOwned(userId, appointmentId));
        log.info("일정 삭제 userId={} appointmentId={}", userId, appointmentId);
    }

    // ---------- 내부 ----------

    private List<AppointmentResponse> listBetween(Long userId, Instant from, Instant to) {
        return appointmentRepository
                .findAllByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        userId, from, to)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /** 남의 카드를 자기 일정에 붙일 수 없다. findOwned 가 소유를 검사한다. */
    private BriefingCard findCard(Long userId, Long cardId) {
        return cardId == null ? null : briefingCardService.findOwned(userId, cardId);
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
