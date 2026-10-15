package com.jinryomate.backend.home.service;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.appointment.service.AppointmentService;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.home.dto.HomeDtos.HomeResponse;
import com.jinryomate.backend.home.dto.HomeDtos.HomeResponse.InProgressSession;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitSummary;
import com.jinryomate.backend.visit.service.VisitRecordService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 요약 (화면 1n).
 *
 * <p>자기 데이터를 갖지 않고 다른 도메인을 모으기만 한다.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 홈에 보여줄 최근 카드 수. 화면이 3건을 보여주고 "전체 보기"로 넘어간다. */
    private static final int RECENT_CARD_LIMIT = 3;

    private final BriefingCardService briefingCardService;
    private final VisitRecordService visitRecordService;
    private final AppointmentService appointmentService;
    private final IntakeSessionRepository intakeSessionRepository;
    private final BriefingCardRepository briefingCardRepository;

    /**
     * 홈에 필요한 것을 한 번에 모은다.
     *
     * <p>신규 사용자는 전부 비어서 나간다. 오류가 아니라 정상 상태다 — 앱이 그걸 보고
     * "증상 정리 유도" 화면(1n-2)을 그린다.
     */
    @Transactional(readOnly = true)
    public HomeResponse get(Long userId) {
        return new HomeResponse(
                lastVisitedOn(userId),
                nextAppointment(userId),
                appointmentService.findPendingRecordOn(userId),
                inProgressSession(userId),
                recentCards(userId));
    }

    /** 마지막 진료일. "지난 진료 후 n일"의 재료다 — n 은 앱이 센다. */
    private LocalDate lastVisitedOn(Long userId) {
        // 목록이 최근 진료일 순이라 첫 건이 마지막 진료다.
        return visitRecordService.list(userId).stream()
                .findFirst()
                .map(VisitSummary::visitedOn)
                .orElse(null);
    }

    /** 다음 예정 일정. D-day 는 앱이 센다. */
    private AppointmentResponse nextAppointment(Long userId) {
        return appointmentService.listUpcoming(userId).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 이어서 할 증상 정리. <b>가장 최근 세션이 아직 카드가 없을 때만</b> 낸다.
     *
     * <p><b>끝난 문답도 담는다(#171).</b> 문답 3단계에서는 문답이 끝난 뒤에도 같은 채팅에서
     * 질문을 고르고 나서야 카드를 만든다. 그 사이에 나가면 세션은 {@code COMPLETED} 인데
     * 카드가 없어, {@code IN_PROGRESS} 만 보면 돌아갈 길이 사라진다.
     *
     * <p>카드가 있으면 그 문답은 끝까지 간 것이다. 카드를 만들면 세션도 끝나므로
     * ({@code BriefingCardService}) 카드 있는 진행 중 세션은 없다.
     *
     * <p><b>가장 최근 세션만 본다.</b> 그게 카드까지 갔으면 옛 세션을 거슬러 찾지 않는다 —
     * 그러면 방금 카드를 만든 사람에게 몇 주 전에 놓은 문답이 다시 뜬다.
     */
    private InProgressSession inProgressSession(Long userId) {
        return intakeSessionRepository
                .findFirstByUserIdAndStatusNotOrderByStartedAtDescIdDesc(
                        userId, IntakeSession.Status.ABANDONED)
                .filter(s -> !briefingCardRepository.existsBySessionId(s.getId()))
                .map(InProgressSession::from)
                .orElse(null);
    }

    private List<CardSummary> recentCards(Long userId) {
        return briefingCardService.list(userId).stream()
                .limit(RECENT_CARD_LIMIT)
                .toList();
    }
}
