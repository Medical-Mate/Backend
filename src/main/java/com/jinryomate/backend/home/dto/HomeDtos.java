package com.jinryomate.backend.home.dto;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.time.LocalDate;
import java.util.List;

/** 홈 화면의 응답 (화면 1n). */
public final class HomeDtos {

    private HomeDtos() {}

    /**
     * 홈에 필요한 것 전부.
     *
     * <p><b>문구를 담지 않는다.</b> "지난 진료 후 12일이 지났어요"는 앱이 만든다.
     * 서버가 문자열로 내려주면 말투를 바꿀 때마다 배포해야 하고, 시간대가 어긋나면
     * 날짜 수가 하루 틀린다.
     *
     * <p>신규 사용자는 전부 {@code null} 또는 빈 배열이다. 오류가 아니라 정상 상태다 —
     * 앱은 이걸 보고 "증상 정리 유도" 화면(1n-2)을 그린다.
     *
     * @param lastVisitedOn     마지막 진료일. "지난 진료 후 n일"의 재료
     * @param nextAppointment   다음 예정 일정. D-day 는 앱이 센다
     * @param inProgressSession 작성 중이던 증상 정리. "이어서 하기"
     * @param recentCards       최근 브리핑 카드 3건
     */
    public record HomeResponse(
            LocalDate lastVisitedOn,
            AppointmentResponse nextAppointment,
            InProgressSession inProgressSession,
            List<CardSummary> recentCards
    ) {

        /**
         * 이어서 할 문답 세션.
         *
         * <p>화면의 "복부 통증 · 3단계 중 2단계까지 답했어요"가 이 셋으로 만들어진다.
         */
        public record InProgressSession(
                Long sessionId,
                String siteText,
                int progressCurrent,
                int progressTotal
        ) {
            public static InProgressSession from(IntakeSession s) {
                return new InProgressSession(
                        s.getId(),
                        s.getSiteText(),
                        s.getProgressCurrent(),
                        s.getProgressTotal());
            }
        }
    }
}
