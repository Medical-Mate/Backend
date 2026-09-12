package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiMemoClient;
import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 메모 분류 응답을 우리 축으로 옮기는 부분.
 *
 * <p>본문은 <b>배포된 AI 서버에 실제로 서명해서 불러 받은 응답</b>이다. 손으로 지어낸
 * 것이면 우리가 상상한 모양만 통과하고, 정작 계약이 어긋났을 때 안 깨진다.
 */
class HttpAiMemoClientTest {

    /**
     * 실제 응답. {@code tests} 축의 {@code value} 가 {@code null} 인 것이 중요하다 —
     * AI 가 못 찾은 축은 이렇게 온다.
     */
    private static final String REAL_RESPONSE = """
            {
              "card": {
                "card_type": "postvisit",
                "chief_complaint": null,
                "axes": {
                  "findings": {
                    "status": "filled",
                    "value": "선생님이 허리 디스크 초기래요.",
                    "evidence": ["선생님이 허리 디스크 초기래요."],
                    "source": "ai_extraction"
                  },
                  "tests": {
                    "status": "unknown",
                    "value": null,
                    "evidence": [],
                    "source": null
                  },
                  "medication_instructions": {
                    "status": "filled",
                    "value": "나프록센 500mg 하루 두 번 식후에 먹으라고 하셨고",
                    "evidence": ["나프록센 500mg 하루 두 번 식후에 먹으라고 하셨고"],
                    "source": "ai_extraction"
                  },
                  "follow_up": {
                    "status": "unknown",
                    "value": null,
                    "evidence": [],
                    "source": null
                  }
                },
                "red_flags": [],
                "patient_notes": ["무거운 거 들지 말라고 하셨어요."],
                "minimally_complete": true,
                "completeness": 1.0,
                "provenance": {
                  "prompt_version": "memo-small-v4",
                  "model_id": "apac.amazon.nova-pro-v1:0",
                  "ontology_snapshot": "f848848baea4"
                },
                "widening": [],
                "site_comparison": null,
                "document_codes": [],
                "memo": "선생님이 허리 디스크 초기래요. 나프록센 …",
                "unsorted": ["못 알아들은 문장"],
                "follow_up_date": null,
                "visit_date": "2026-09-12",
                "clinic": "OO정형외과"
              },
              "sentences": ["선생님이 허리 디스크 초기래요.", "나프록센 500mg 하루 두 번 식후에 먹으라고 하셨고"],
              "labels": {"0": "findings", "1": "medication_instructions"},
              "dropped": [],
              "source": "server",
              "usage": {"input_tokens": 1656, "output_tokens": 31, "cost_usd": 0.001424},
              "request_id": null
            }
            """;

    @Test
    @DisplayName("실제 응답을 우리 축으로 옮긴다. 못 찾은 축의 value 는 null 이다")
    void 응답_변환() {
        MemoClassification result = classifyWith(REAL_RESPONSE);

        assertThat(result.axes()).containsOnlyKeys(
                "findings", "tests", "medication_instructions", "follow_up");

        var findings = result.axes().get("findings");
        assertThat(findings.getStatus()).isEqualTo(AxisStatus.FILLED);
        assertThat(findings.getSource()).isEqualTo(AxisSource.AI_EXTRACTION);
        assertThat(findings.getValue()).isEqualTo("선생님이 허리 디스크 초기래요.");
        assertThat(findings.getEvidence()).containsExactly("선생님이 허리 디스크 초기래요.");

        // JSON 의 null 이 문자열 "null" 로 새면 의사 화면에 "null" 이 찍힌다.
        var tests = result.axes().get("tests");
        assertThat(tests.getValue()).isNull();
        assertThat(tests.getStatus()).isEqualTo(AxisStatus.UNKNOWN);
        assertThat(tests.getSource()).isNull();
        assertThat(tests.getEvidence()).isEmpty();

        // 어느 축에도 안 들어간 문장은 둘 다 모은다. 버리지 않는다.
        assertThat(result.patientNotes())
                .containsExactly("무거운 거 들지 말라고 하셨어요.", "못 알아들은 문장");

        assertThat(result.sentences()).hasSize(2);
        assertThat(result.labels()).containsEntry("0", "findings");
        assertThat(result.followUpDate()).isNull();
        assertThat(result.promptVersion()).isEqualTo("memo-small-v4");
        assertThat(result.modelId()).isEqualTo("apac.amazon.nova-pro-v1:0");
    }

    @Test
    @DisplayName("재방문 날짜가 오면 읽고, 읽을 수 없으면 비운다")
    void 재방문_날짜() {
        assertThat(classifyWith(REAL_RESPONSE.replace(
                "\"follow_up_date\": null", "\"follow_up_date\": \"2026-09-26\""))
                .followUpDate()).isEqualTo(LocalDate.of(2026, 9, 26));

        // 여기서 실패시키면 나머지 분류까지 버리게 된다. 환자가 화면에서 고칠 수 있는 값이다.
        assertThat(classifyWith(REAL_RESPONSE.replace(
                "\"follow_up_date\": null", "\"follow_up_date\": \"다음 주 화요일\""))
                .followUpDate()).isNull();
    }

    @Test
    @DisplayName("모르는 status 가 와도 분류를 버리지 않는다")
    void 모르는_값() {
        MemoClassification result = classifyWith(
                REAL_RESPONSE.replace("\"status\": \"filled\"", "\"status\": \"partially_filled\""));

        // 축 하나를 "아직 안 물어봤다"로 두는 손해가, 메모가 날아가는 손해보다 작다.
        assertThat(result.axes().get("findings").getStatus()).isEqualTo(AxisStatus.NOT_ASKED);
        assertThat(result.axes().get("findings").getValue()).isEqualTo("선생님이 허리 디스크 초기래요.");
    }

    @Test
    @DisplayName("서명 헤더 셋을 붙여 보낸다")
    void 서명() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andExpect(header(AiSigner.SIGNATURE_HEADER, Matchers.matchesRegex("[0-9a-f]{64}")))
                .andExpect(header(AiSigner.TIMESTAMP_HEADER, Matchers.matchesRegex("\\d+")))
                .andExpect(header(AiSigner.REQUEST_ID_HEADER, Matchers.not(Matchers.blankOrNullString())))
                .andRespond(withSuccess(REAL_RESPONSE, MediaType.APPLICATION_JSON));

        new HttpAiMemoClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .classify("메모", null, null, null);

        server.verify();
    }

    private MemoClassification classifyWith(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        return new HttpAiMemoClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .classify("메모", LocalDate.of(2026, 9, 12), "OO정형외과", null);
    }
}
