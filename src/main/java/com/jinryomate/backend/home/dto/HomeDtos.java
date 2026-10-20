package com.jinryomate.backend.home.dto;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.time.Instant;
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
     * @param inProgressSession 이어서 할 증상 정리. 문답이 끝났어도 카드를 아직 안 만들었으면 온다
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
         * 이어서 할 문답 세션. <b>가장 최근 세션이 아직 카드가 없을 때만</b> 온다(#171).
         *
         * <p>{@code progressCurrent}·{@code progressTotal} 은 <b>대화 턴</b> 진행도다.
         * 화면 단계(n/3)가 아니다.
         */
        public record InProgressSession(
                Long sessionId,
                String siteText,
                int progressCurrent,
                int progressTotal,

                /**
                 * 문답이 끝났는지. <b>턴 응답의 {@code ended} 와 같은 뜻이다.</b>
                 *
                 * <p>{@code true} 면 문답은 끝났고 질문 고르기({@code 1c-D})에서 멈춘 것이다 —
                 * 이어서 하기를 누르면 그 자리로 보낸다. {@code false} 면 문답 채팅으로 보낸다.
                 * 이 값이 없으면 앱이 홈에 들어올 때마다 세션을 한 번 더 읽어야 한다.
                 */
                boolean ended,

                /**
                 * 문답을 시작한 시각(#179).
                 *
                 * <p>카드 목록(1j-4)이 이 줄을 카드들과 날짜순으로 섞고 월 묶음과
                 * "오늘"·"MM.dd" 를 정한다. 날짜 계산은 앱이 한다 — 서버가 날짜로 잘라
                 * 내려주면 시간대가 어긋날 때 하루 틀린다.
                 */
                Instant startedAt,

                /**
                 * 질문 고르기({@code 1c-D})에서 "다음"을 눌렀는지(#185).
                 *
                 * <p>{@code ended} 만으로는 질문 고르기에서 나간 사람과 다음까지 누른 사람을
                 * 못 가른다. {@code true} 면 "임시 저장"(3/3, 이어서 하기는 증상 정리 완료
                 * {@code 1c-5} 로), {@code false} 면 "작성 중"이다.
                 */
                boolean questionsConfirmed
        ) {
            public static InProgressSession from(IntakeSession s) {
                return new InProgressSession(
                        s.getId(),
                        s.getSiteText(),
                        s.getProgressCurrent(),
                        s.getProgressTotal(),
                        s.isCompleted(),
                        s.getStartedAt(),
                        s.isQuestionsConfirmed());
            }
        }
    }
}
