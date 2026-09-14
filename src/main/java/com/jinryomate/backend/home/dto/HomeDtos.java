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

            /**
             * 진료 후 기록이 아직 없는 <b>지난 일정</b> 가운데 가장 최근 날.
             * 없으면 {@code null} 입니다.
             *
             * <p>화면의 "9월 12일 진료, 기록이 아직 없어요" 입니다. 날짜 하나면 문구를
             * 만들 수 있어 일정을 통째로 내지 않습니다.
             *
             * <p><b>14일까지만 거슬러 봅니다.</b> 그보다 오래된 것을 이제 와 알리는 것은
             * 때를 놓친 알림입니다.
             *
             * <p><b>기록이 있는지는 날짜로 견줍니다.</b> 일정과 기록을 잇는 열쇠가 없어서
             * "그날 날짜로 남긴 기록이 있는가"로 판단합니다 — 진료 다음 날 적으면서 날짜를
             * 그날로 두면 이 일정은 계속 "기록 없음"으로 남습니다.
             */
            LocalDate pendingRecordOn,

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
