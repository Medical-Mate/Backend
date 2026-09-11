package com.jinryomate.backend.intake.dto;

import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.entity.Side;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 문답 세션의 요청·응답. */
public final class IntakeDtos {

    private IntakeDtos() {}

    /**
     * 세션 시작. 화면 S1.5에서 짚은 부위를 함께 보낸다.
     *
     * <p>부위를 건너뛰어도 시작할 수 있다. 와이어프레임에 "부위 짚기"를 건너뛰는 경로가 있다.
     */
    public record StartSessionRequest(
            /**
             * 부위 마스터의 노드 id. {@code ANC:001} · {@code SUR:032}.
             *
             * <p><b>하나만 보낸다.</b> AI 가 세션 시작에 부위 하나를 받는다. 없는 id 는 400이다.
             */
            @Size(max = 40, message = "부위 코드가 너무 깁니다.")
            String siteNodeId,

            /** 좌우. 좌우가 없는 부위(머리·배 등 13곳)에 보내면 400이다. */
            Side side,

            @Size(max = 100, message = "부위 표현은 100자 이내입니다.")
            String siteText
    ) {}

    /**
     * 환자 발화 한 줄.
     *
     * <p>음성으로 말했어도 <b>오디오를 받지 않는다.</b> 앱이 변환한 텍스트만 온다 —
     * "녹음은 저장하지 않는다"는 원칙 때문이다. {@code inputMethod} 는 그게
     * 타이핑이었는지 음성이었는지만 남긴다.
     *
     * <p>2000자 상한은 {@code intake_messages.text} 컬럼 길이다. AI 는 300자를 넘으면
     * 잘라서 반영하고 안내 문구를 붙이는데, 그건 AI 쪽 동작이라 여기서 막지 않는다.
     */
    public record SendMessageRequest(
            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = 2000, message = "한 번에 2000자까지 보낼 수 있습니다.")
            String text,

            /** 비우면 {@code TEXT} 로 본다. */
            IntakeMessage.InputMethod inputMethod
    ) {}

    /**
     * 통증 강도. 화면 3단계({@code 1d}) 슬라이더.
     *
     * <p><b>1~5 서열척도다. NRS 0~10 이 아니다.</b> 와이어프레임 슬라이더가 다섯 칸이다.
     *
     * <p>{@code label} 은 앱이 보낸다 — "꽤 아파요" 같은 문구는 디자인 카피라 서버가
     * 들고 있으면 바꿀 때마다 배포해야 한다.
     */
    public record SeverityRequest(
            @NotNull(message = "통증 정도를 골라주세요.")
            @Min(value = 1, message = "통증 정도는 1~5입니다.")
            @Max(value = 5, message = "통증 정도는 1~5입니다.")
            Integer level,

            @Size(max = 40, message = "표시 문구는 40자 이내입니다.")
            String label
    ) {}

    /**
     * 의사에게 물어볼 것. 화면 4단계({@code 1i}).
     *
     * <p><b>목록을 통째로 보낸다.</b> 추가·편집·삭제·순서변경이 전부 이 한 번으로 처리된다.
     * 빈 배열을 보내면 전부 지운다.
     */
    public record QuestionsRequest(
            @NotNull(message = "질문 목록이 필요합니다. 비우려면 빈 배열을 보내주세요.")
            @Size(max = MAX_QUESTIONS, message = "질문은 최대 " + MAX_QUESTIONS + "개까지입니다.")
            List<@NotBlank(message = "빈 질문은 담을 수 없습니다.")
                 @Size(max = 200, message = "질문은 200자 이내입니다.") String> questions
    ) {}

    /** 화면 {@code 1i} 의 "적어둔 질문 · 최대 3개". */
    public static final int MAX_QUESTIONS = 3;

    public record MessageResponse(int seq, IntakeMessage.Role role, String text) {
        public static MessageResponse from(IntakeMessage m) {
            return new MessageResponse(m.getSeq(), m.getRole(), m.getText());
        }
    }

    /**
     * 턴 처리 결과.
     *
     * <p>{@code messages} 에 대화 전체를 다시 실어 보낸다. 앱이 화면을 다시 그릴 때
     * 세션을 또 조회하지 않아도 되고, 중간에 유실된 줄이 있어도 여기서 맞춰진다.
     */
    public record TurnResponse(
            Long sessionId,
            IntakeSession.Status status,
            /** 환자에게 보여줄 다음 문장. 질문이거나 마무리 인사다. */
            String reply,
            boolean ended,
            String endReason,
            SessionResponse.Progress progress,
            List<MessageResponse> messages
    ) {
        public static TurnResponse of(IntakeSession s, String reply, boolean ended) {
            return new TurnResponse(
                    s.getId(),
                    s.getStatus(),
                    reply,
                    ended,
                    s.getEndReason(),
                    new SessionResponse.Progress(s.getProgressCurrent(), s.getProgressTotal()),
                    s.getMessages().stream().map(MessageResponse::from).toList());
        }
    }

    /**
     * 세션 전체.
     *
     * <p>3·4단계 값도 함께 담는다. 사용자가 뒤로 갔을 때 <b>고른 값이 그대로 복원</b>돼야
     * 하는데, 세션 조회 하나로 화면 넷을 다 그릴 수 있어야 그게 된다.
     */
    public record SessionResponse(
            Long sessionId,
            IntakeSession.Status status,
            String siteNodeId,
            Side side,
            String siteText,
            Progress progress,
            List<MessageResponse> messages,
            /** 3단계. 아직 안 골랐으면 {@code null}. */
            Severity severity,
            /** 4단계. 아직 안 적었으면 빈 배열. */
            List<String> questions
    ) {
        public record Progress(int current, int total) {}

        public record Severity(int level, String label) {}

        public static SessionResponse from(IntakeSession s) {
            return new SessionResponse(
                    s.getId(),
                    s.getStatus(),
                    s.getSiteNodeId(),
                    s.getSide(),
                    s.getSiteText(),
                    new Progress(s.getProgressCurrent(), s.getProgressTotal()),
                    s.getMessages().stream().map(MessageResponse::from).toList(),
                    s.getSeverityLevel() == null
                            ? null
                            : new Severity(s.getSeverityLevel(), s.getSeverityLabel()),
                    List.copyOf(s.getQuestions()));
        }
    }
}
