package com.jinryomate.backend.handoff.dto;

import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.Department;
import com.jinryomate.backend.card.entity.Medication;
import com.jinryomate.backend.handoff.entity.ShareLink;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import java.time.Instant;
import java.util.List;

/** 진료실 전달(S4)과 공유 링크(S6)의 응답. */
public final class HandoffDtos {

    private HandoffDtos() {}

    // ---------- 전달 화면 ----------

    /**
     * 의사에게 보여주는 카드.
     *
     * <p>{@code CardResponse} 를 그대로 쓰지 않는다. 거기엔 {@code evidence},
     * {@code pipelineVersion}, {@code aiRequestId}, {@code sessionId} 가 들어 있는데
     * <b>의사에게 AI 파이프라인 버전을 보여줄 이유가 없다</b>. 낭독 모드 큰 글자
     * 화면에 쓸 것만 남긴다.
     *
     * <p>{@code status} 3값은 그대로 내려준다. 앱이 "알레르기: 본인 확인 못 함"을
     * 찍으려면 {@code none}("없어요")과 {@code unknown}("잘 모르겠어요")이 구분돼야 한다.
     */
    public record HandoffView(
            Patient patient,
            String title,
            Field onset,
            Field pattern,
            Site site,
            Medications medications,
            Field allergies,
            List<String> questions,
            Department suggestedDepartment,
            Instant confirmedAt
    ) {
        public record Patient(String name, Integer age, Sex sex) {}

        public record Field(FieldStatus status, String text) {}

        public record Site(FieldStatus status, String text, List<String> codes) {}

        public record Medications(FieldStatus status, List<Medication> items) {}

        public static HandoffView from(BriefingCard c) {
            return new HandoffView(
                    new Patient(c.getPatientName(), c.getPatientAge(), c.getPatientSex()),
                    c.getTitle(),
                    new Field(c.getOnsetStatus(), c.getOnsetText()),
                    new Field(c.getPatternStatus(), c.getPatternText()),
                    new Site(c.getSiteStatus(), c.getSiteText(), List.copyOf(c.getSiteCodes())),
                    new Medications(c.getMedicationsStatus(), List.copyOf(c.getMedications())),
                    new Field(c.getAllergiesStatus(), c.getAllergiesText()),
                    List.copyOf(c.getQuestions()),
                    c.getSuggestedDepartment(),
                    c.getConfirmedAt());
        }
    }

    // ---------- 공유 링크 ----------

    /**
     * 링크를 막 발급했을 때. {@code url} 은 앱이 그대로 공유하면 되는 주소다.
     *
     * <p>{@code cardId} 를 담지 않는다. 공유될 값에 내부 식별자를 실을 이유가 없다.
     */
    public record ShareLinkResponse(
            Long shareLinkId,
            String token,
            String url,
            Instant expiresAt,
            Instant revokedAt,
            Instant viewedAt,
            int viewCount,
            Instant createdAt
    ) {
        public static ShareLinkResponse of(ShareLink link, String baseUrl) {
            return new ShareLinkResponse(
                    link.getId(),
                    link.getToken(),
                    baseUrl + "/s/" + link.getToken(),
                    link.getExpiresAt(),
                    link.getRevokedAt(),
                    link.getViewedAt(),
                    link.getViewCount(),
                    link.getCreatedAt());
        }
    }
}
