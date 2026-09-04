package com.jinryomate.backend.intake.dto;

import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
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

    public record MessageResponse(int seq, IntakeMessage.Role role, String text) {
        public static MessageResponse from(IntakeMessage m) {
            return new MessageResponse(m.getSeq(), m.getRole(), m.getText());
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
