package com.jinryomate.backend.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.entity.Department;
import com.jinryomate.backend.card.entity.Medication;
import com.jinryomate.backend.card.service.CardContentValidator;
import com.jinryomate.backend.profile.entity.FieldStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카드 검증 규칙.
 *
 * <p>스프링 없이 도는 순수 로직 테스트다. 규칙이 와이어프레임과 낭독 모드에서 나온 것이라
 * 무엇이 왜 걸리는지가 여기 남아야 한다.
 */
class CardContentValidatorTest {

    private final CardContentValidator validator = new CardContentValidator();

    @Test
    @DisplayName("제목에 진단명이 들어가면 걸러내고 기본 제목으로 바꾼다")
    void 진단명_금지() {
        CardContentValidator.Result result = validator.validate(content("류마티스 의심"));

        assertThat(result.content().title()).isEqualTo("증상 정리");
        assertThat(result.rejectedFields()).contains("title(진단명)");
    }

    @Test
    @DisplayName("정상 제목은 그대로 통과한다")
    void 정상_제목() {
        CardContentValidator.Result result = validator.validate(content("손가락 경직·부종"));

        assertThat(result.content().title()).isEqualTo("손가락 경직·부종");
        assertThat(result.hasRejection()).isFalse();
    }

    @Test
    @DisplayName("제목이 20자를 넘으면 잘라서라도 남긴다")
    void 제목_길이() {
        CardContentValidator.Result result = validator.validate(content("가".repeat(30)));

        assertThat(result.content().title()).hasSize(20);
        assertThat(result.rejectedFields()).contains("title(길이)");
    }

    @Test
    @DisplayName("텍스트가 80자를 넘으면 UNKNOWN으로 낮춘다")
    void 텍스트_길이() {
        CardContent raw = new CardContent(
                "손가락 경직",
                FieldStatus.KNOWN, "가".repeat(100),
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.UNKNOWN, List.of(),
                FieldStatus.UNKNOWN, null,
                List.of(), null, Map.of());

        CardContentValidator.Result result = validator.validate(raw);

        // 버리는 게 아니라 환자가 직접 채우도록 UNKNOWN 으로 남긴다.
        assertThat(result.content().onsetStatus()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(result.content().onsetText()).isNull();
        assertThat(result.rejectedFields()).contains("onset(길이)");
    }

    @Test
    @DisplayName("NONE과 UNKNOWN은 값 없이도 그대로 보존된다")
    void 세_값_상태_보존() {
        CardContent raw = new CardContent(
                "손가락 경직",
                FieldStatus.NONE, null,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.UNKNOWN, List.of(),
                FieldStatus.NONE, null,
                List.of(), null, Map.of());

        CardContentValidator.Result result = validator.validate(raw);

        // "없어요"를 "잘 모르겠어요"로 뭉개면 카드 문구가 달라진다.
        assertThat(result.content().onsetStatus()).isEqualTo(FieldStatus.NONE);
        assertThat(result.content().allergiesStatus()).isEqualTo(FieldStatus.NONE);
        assertThat(result.hasRejection()).isFalse();
    }

    @Test
    @DisplayName("질문은 3개까지만 남고 40자를 넘으면 버린다")
    void 질문_제한() {
        CardContent raw = new CardContent(
                "손가락 경직",
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.UNKNOWN, List.of(),
                FieldStatus.UNKNOWN, null,
                List.of("검사가 필요한가요?", "진통제 계속 먹어도 되나요?", "재방문 기준은?",
                        "네 번째 질문", "가".repeat(50)),
                null, Map.of());

        CardContentValidator.Result result = validator.validate(raw);

        assertThat(result.content().questions()).hasSize(3);
        assertThat(result.content().questions()).containsExactly(
                "검사가 필요한가요?", "진통제 계속 먹어도 되나요?", "재방문 기준은?");
        assertThat(result.rejectedFields()).contains("questions");
    }

    @Test
    @DisplayName("복용약이 KNOWN이 아니면 항목을 비운다")
    void 복용약_상태_불일치() {
        CardContent raw = new CardContent(
                "손가락 경직",
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.NONE, List.of(new Medication("이부프로펜", null)),
                FieldStatus.UNKNOWN, null,
                List.of(), null, Map.of());

        CardContentValidator.Result result = validator.validate(raw);

        // "없어요"라고 해놓고 약이 들어 있으면 카드가 앞뒤가 안 맞는다.
        assertThat(result.content().medications()).isEmpty();
    }

    @Test
    @DisplayName("진료과는 enum 값 그대로 통과한다")
    void 진료과() {
        CardContent raw = new CardContent(
                "손가락 경직",
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.UNKNOWN, List.of(),
                FieldStatus.UNKNOWN, null,
                List.of(), Department.ORTHOPEDICS, Map.of());

        assertThat(validator.validate(raw).content().suggestedDepartment())
                .isEqualTo(Department.ORTHOPEDICS);
    }

    private CardContent content(String title) {
        return new CardContent(
                title,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null,
                FieldStatus.UNKNOWN, null, List.of(),
                FieldStatus.UNKNOWN, List.of(),
                FieldStatus.UNKNOWN, null,
                List.of(), null, Map.of());
    }
}
