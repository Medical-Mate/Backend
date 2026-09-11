package com.jinryomate.backend.intake.dto;

import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import jakarta.validation.constraints.NotBlank;
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
            @Size(max = 10, message = "부위는 최대 10개까지입니다.")
            List<@Size(max = 64) String> siteCodes,

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

    public record SessionResponse(
            Long sessionId,
            IntakeSession.Status status,
            List<String> siteCodes,
            String siteText,
            Progress progress,
            List<MessageResponse> messages
    ) {
        public record Progress(int current, int total) {}

        public static SessionResponse from(IntakeSession s) {
            return new SessionResponse(
                    s.getId(),
                    s.getStatus(),
                    List.copyOf(s.getSiteCodes()),
                    s.getSiteText(),
                    new Progress(s.getProgressCurrent(), s.getProgressTotal()),
                    s.getMessages().stream().map(MessageResponse::from).toList());
        }
    }
}
