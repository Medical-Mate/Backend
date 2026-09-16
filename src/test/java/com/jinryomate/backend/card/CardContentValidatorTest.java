package com.jinryomate.backend.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.service.CardContentValidator;
import com.jinryomate.backend.intake.dto.IntakeDtos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카드 검증 규칙.
 *
 * <p>스프링 없이 도는 순수 로직 테스트다. 규칙이 와이어프레임과 낭독 모드에서 나온 것이라
 * 무엇이 왜 걸리는지가 여기 남아야 한다.
 *
 * <p><b>검증에 걸려도 예외를 던지지 않는다.</b> 카드 생성이 통째로 실패하면 환자가 한
 * 문답이 날아간다. 걸린 값만 {@code UNKNOWN} 으로 낮추고 무엇이 걸렸는지 목록으로 돌려준다.
 */
class CardContentValidatorTest {

    private final CardContentValidator validator = new CardContentValidator();

    @Test
    @DisplayName("제목은 검증하지 않는다")
    void 제목_무검증() {
        // AI 가 부위 + 기간을 결정론으로 조합해 만든다. LLM 을 거치지 않는 값의 조합이라
        // 병명이 들어갈 경로 자체가 없다 — 검사를 느슨하게 한 게 아니라 대상이 없다.
        CardContentValidator.Result result = validator.validate(content(c -> {}, "왼쪽 무릎 · 3일"));

        assertThat(result.content().title()).isEqualTo("왼쪽 무릎 · 3일");
        assertThat(result.rejectedFields()).isEmpty();
    }

    @Test
    @DisplayName("아직 안 물어본 축이 대부분이어도 통과한다")
    void 기본값_not_asked() {
        // 1턴째 카드는 8축 중 대부분이 not_asked 다. 3값으로 검증하면 여기서 전부 떨어진다.
        CardContentValidator.Result result = validator.validate(content(axes -> {
            axes.put("site", CardAxis.of("site", AxisStatus.FILLED, "왼쪽 무릎", List.of("왼쪽 무릎이"), null));
            axes.put("onset", CardAxis.notAsked("onset"));
            axes.put("severity", CardAxis.notAsked("severity"));
        }, "왼쪽 무릎"));

        assertThat(result.rejectedFields()).isEmpty();
        assertThat(result.content().axes().get("onset").getStatus()).isEqualTo(AxisStatus.NOT_ASKED);
    }

    @Test
    @DisplayName("축 값이 80자를 넘으면 모르겠다로 낮춘다")
    void 축_길이() {
        // 80자는 낭독 모드 큰 글자 기준이다. 넘으면 화면에서 잘린다.
        String tooLong = "아".repeat(81);
        CardContentValidator.Result result = validator.validate(content(axes ->
                axes.put("onset", CardAxis.of("onset", AxisStatus.FILLED, tooLong, List.of("어제"), null)),
                "제목"));

        assertThat(result.content().axes().get("onset").getStatus()).isEqualTo(AxisStatus.UNKNOWN);
        assertThat(result.content().axes().get("onset").getValue()).isNull();
        assertThat(result.rejectedFields()).contains("axes.onset");
    }

    @Test
    @DisplayName("값이 있다면서 비어 있으면 모르겠다로 낮춘다")
    void 빈_값() {
        // 화면에 빈 줄이 찍히느니 모른다고 하는 편이 정직하다.
        CardContentValidator.Result result = validator.validate(content(axes ->
                axes.put("character", CardAxis.of("character", AxisStatus.FILLED, "  ", List.of(), null)),
                "제목"));

        assertThat(result.content().axes().get("character").getStatus()).isEqualTo(AxisStatus.UNKNOWN);
        assertThat(result.rejectedFields()).contains("axes.character");
    }

    @Test
    @DisplayName("40자를 넘어도 버리지 않는다 — 환자가 쓴 글이다")
    void 질문_길이() {
        // 40 자는 AI 가 만든 문구의 기준이었는데, 이 목록의 출처는 환자가 직접 쓴
        // session.getQuestions() 하나뿐이다. 실제로 41 자 질문이 저장은 200 으로 되고
        // 카드를 만들 때 통째로 사라졌다(#132).
        CardContentValidator.Result result = validator.validate(content(
                axes -> {}, "제목",
                List.of("짧은 질문인가요?", "가".repeat(41)),
                List.of()));

        assertThat(result.content().questions()).containsExactly("짧은 질문인가요?", "가".repeat(41));
        assertThat(result.rejectedFields()).doesNotContain("questions");
    }

    @Test
    @DisplayName("질문 상한이 저장 쪽과 같다")
    void 질문_상한이_한_곳에서_온다() {
        // 여기가 따로 3 을 들고 있어서 겪었다 — 저장은 5 개를 받는데 카드를 만들 때
        // 앞에서 잘렸다. 상수를 나눠 갖지 말고 같은 값을 보게 한다.
        List<String> full = IntStream.rangeClosed(1, IntakeDtos.MAX_QUESTIONS)
                .mapToObj(i -> "질문 " + i)
                .toList();

        CardContentValidator.Result result = validator.validate(content(
                axes -> {}, "제목", full, List.of()));

        assertThat(result.content().questions()).isEqualTo(full);
        assertThat(result.rejectedFields()).doesNotContain("questions");
    }

    @Test
    @DisplayName("상한을 넘으면 앞에서 자른다")
    void 질문_개수() {
        // 버리지 않고 앞에서 자르는 이유 — 순서가 중요도다.
        List<String> tooMany = IntStream.rangeClosed(1, IntakeDtos.MAX_QUESTIONS + 1)
                .mapToObj(i -> "질문 " + i)
                .toList();

        CardContentValidator.Result result = validator.validate(content(
                axes -> {}, "제목", tooMany, List.of()));

        assertThat(result.content().questions())
                .isEqualTo(tooMany.subList(0, IntakeDtos.MAX_QUESTIONS));
        assertThat(result.rejectedFields()).contains("questions");
    }

    @Test
    @DisplayName("진료과는 목록 밖이면 거부한다")
    void 진료과_목록() {
        // enum 을 버린 것이 검증을 버린 것은 아니다. 하나로 좁히지 않을 뿐이다.
        CardContentValidator.Result result = validator.validate(content(
                axes -> {}, "제목", List.of(),
                List.of("내과", "소화기내과", "점집")));

        assertThat(result.content().departmentGuidance()).containsExactly("내과", "소화기내과");
        assertThat(result.rejectedFields()).contains("departmentGuidance");
    }

    @Test
    @DisplayName("진료과가 여러 개여도 하나로 좁히지 않는다")
    void 진료과_배열() {
        // 아랫배(SUR:032)는 넷이 붙는다. 하나로 고르는 순간 그게 감별이 된다.
        CardContentValidator.Result result = validator.validate(content(
                axes -> {}, "제목", List.of(),
                List.of("내과", "소화기내과", "산부인과", "비뇨의학과")));

        assertThat(result.content().departmentGuidance()).hasSize(4);
        assertThat(result.rejectedFields()).isEmpty();
    }

    // ---------- helpers ----------

    private CardContent content(java.util.function.Consumer<Map<String, CardAxis>> axes, String title) {
        return content(axes, title, List.of(), List.of());
    }

    private CardContent content(java.util.function.Consumer<Map<String, CardAxis>> axesSetup,
                                String title,
                                List<String> questions,
                                List<String> departments) {
        Map<String, CardAxis> axes = new LinkedHashMap<>();
        axesSetup.accept(axes);
        return new CardContent(
                title,
                "왼쪽 무릎이 계단 내려갈 때 아파요",
                axes,
                List.of(),
                List.of(),
                questions,
                departments,
                "팀 결정 2026-09-04, 의료인 자문 확인 전",
                0.375,
                false);
    }
}
