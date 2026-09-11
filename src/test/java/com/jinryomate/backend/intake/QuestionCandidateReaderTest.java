package com.jinryomate.backend.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionCandidate;
import com.jinryomate.backend.intake.service.QuestionCandidateReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 카드에서 질문 후보를 꺼내는 규칙.
 *
 * <p>스프링 없이 도는 순수 로직이다. AI 가 아직 후보를 안 내려주므로 <b>여기가 유일한 방어선</b>이다 —
 * 실제로 오기 시작할 때 모양이 다르면 여기서 먼저 깨져야 한다.
 */
class QuestionCandidateReaderTest {

    private final QuestionCandidateReader reader = new QuestionCandidateReader(new ObjectMapper());

    @Test
    @DisplayName("배열 순서가 아니라 rank로 정렬한다")
    void rank_정렬() {
        // 카드를 보면 의사가 바로 아는 것은 낮추고, 복용약·알러지처럼 환자가 꺼내야
        // 처방에 반영되는 것은 올린다 — 그 순서가 rank 에 담긴다.
        List<QuestionCandidate> result = reader.read(card("""
                [{"text":"세 번째","source":"llm","rank":3},
                 {"text":"첫 번째","source":"llm","rank":1},
                 {"text":"두 번째","source":"llm","rank":2}]"""));

        assertThat(result).extracting(QuestionCandidate::text)
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("3개를 넘으면 rank 낮은 것부터 자른다")
    void 상한() {
        // 화면 1i 가 최대 3개다. 뒤에서 자르면 중요한 것이 잘린다.
        List<QuestionCandidate> result = reader.read(card("""
                [{"text":"넷","rank":4},{"text":"하나","rank":1},
                 {"text":"둘","rank":2},{"text":"셋","rank":3}]"""));

        assertThat(result).extracting(QuestionCandidate::text).containsExactly("하나", "둘", "셋");
    }

    @Test
    @DisplayName("rank가 없으면 뒤로 보낸다")
    void rank_없음() {
        List<QuestionCandidate> result = reader.read(card("""
                [{"text":"순번 없음"},{"text":"1번","rank":1}]"""));

        assertThat(result).extracting(QuestionCandidate::text).containsExactly("1번", "순번 없음");
    }

    @Test
    @DisplayName("빈 텍스트는 버린다")
    void 빈_텍스트() {
        List<QuestionCandidate> result = reader.read(card("""
                [{"text":"  ","rank":1},{"text":"진짜 질문","rank":2}]"""));

        assertThat(result).extracting(QuestionCandidate::text).containsExactly("진짜 질문");
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록이다")
    void 후보_없음() {
        // 중간 턴에는 null 이다. 종료 턴에만 온다.
        assertThat(reader.read(card("null"))).isEmpty();
        assertThat(reader.read("{\"card_type\":\"previsit\"}")).isEmpty();
        assertThat(reader.read(null)).isEmpty();
        assertThat(reader.read("  ")).isEmpty();
    }

    @Test
    @DisplayName("카드가 깨져 있어도 세션 조회를 막지 않는다")
    void 깨진_카드() {
        // 후보는 덤이다. 못 읽는다고 실패시키면 화면이 통째로 안 뜬다.
        assertThat(reader.read("{망가짐")).isEmpty();
    }

    private String card(String candidatesJson) {
        return "{\"card_type\":\"previsit\",\"question_candidates\":" + candidatesJson + "}";
    }
}
