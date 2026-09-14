package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiMemoClient;
import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.ai.dto.MemoRequest;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import java.time.LocalDate;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
        assertThat(result.followUp()).isEqualTo(FollowUp.NONE);
        assertThat(result.promptVersion()).isEqualTo("memo-small-v4");
        assertThat(result.modelId()).isEqualTo("apac.amazon.nova-pro-v1:0");
    }

    /**
     * 재방문 시점이 <b>실제로 나왔을 때</b>의 응답. 이것도 서버에서 직접 받은 것이다.
     *
     * <p>계약이 객체다. 처음에 문자열로 읽어서 날짜가 있어도 항상 비워 저장했는데, 그때
     * 테스트에 쓴 표본이 하필 {@code null} 이라 안 걸렸다.
     */
    private static final String WITH_FOLLOW_UP = """
            {
              "card": {
                "axes": {
                  "follow_up": {
                    "status": "filled",
                    "value": "2주 뒤에 다시 오라고 하셨어요.",
                    "evidence": ["2주 뒤에 다시 오라고 하셨어요."],
                    "source": "ai_extraction"
                  }
                },
                "patient_notes": [],
                "unsorted": [],
                "provenance": {"prompt_version": "memo-small-v4", "model_id": "apac.amazon.nova-pro-v1:0"},
                "follow_up_date": {
                  "text": "2주 뒤",
                  "date": "2026-09-27",
                  "approximate": true,
                  "basis": "visit_date 2026-09-13 + 14d"
                },
                "visit_date": "2026-09-13"
              },
              "sentences": ["2주 뒤에 다시 오라고 하셨어요."],
              "labels": {"0": "follow_up"},
              "dropped": [],
              "source": "server",
              "usage": {"input_tokens": 0, "output_tokens": 0, "cost_usd": 0},
              "request_id": null
            }
            """;

    @Test
    @DisplayName("재방문 시점은 객체다. 원문과 '전후' 여부까지 읽는다")
    void 재방문_시점() {
        FollowUp followUp = classifyWith(WITH_FOLLOW_UP).followUp();

        // 날짜만 읽으면 시안의 "2주 뒤 (9월 27일 전후)" 를 못 그린다.
        assertThat(followUp.date()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(followUp.text()).isEqualTo("2주 뒤");
        assertThat(followUp.approximate()).isTrue();
    }

    @Test
    @DisplayName("재방문 시점이 문자열로 와도 견딘다")
    void 재방문_문자열() {
        // 계약이 바뀌었을 때 날짜를 통째로 잃는 것보다 낫다.
        FollowUp followUp = classifyWith(REAL_RESPONSE.replace(
                "\"follow_up_date\": null", "\"follow_up_date\": \"2026-09-26\"")).followUp();

        assertThat(followUp.date()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(followUp.approximate()).isFalse();
    }

    @Test
    @DisplayName("날짜를 못 읽어도 분류 전체를 버리지 않는다")
    void 못_읽는_날짜() {
        // 환자가 1q-1 에서 눈으로 보고 고칠 수 있는 값이라 비어 있어도 흐름이 안 끊긴다.
        var result = classifyWith(WITH_FOLLOW_UP.replace("\"2026-09-27\"", "\"다음 주 화요일\""));

        assertThat(result.followUp().date()).isNull();
        // 원문은 남는다. 날짜만 못 읽은 것이지 재방문 얘기가 없었던 게 아니다.
        assertThat(result.followUp().text()).isEqualTo("2주 뒤");
        assertThat(result.axes().get("follow_up").getValue()).isEqualTo("2주 뒤에 다시 오라고 하셨어요.");
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
    @DisplayName("split_version 은 최상위에서 읽는다")
    void 분리_버전_읽기() {
        // provenance 안이 아니라 응답 최상위다. 거기서 찾으면 항상 null 이 되고,
        // 되보낼 값이 없어져 검사가 조용히 꺼진다.
        MemoClassification result = classifyWith(
                REAL_RESPONSE.replace("\"request_id\": null", "\"split_version\": \"split-v2\", \"request_id\": null"));

        assertThat(result.splitVersion()).isEqualTo("split-v2");
    }

    @Test
    @DisplayName("split_version 이 안 와도 견딘다")
    void 분리_버전_없음() {
        // 계약이 붙기 전 이미지에서는 안 온다. 그때는 검사가 꺼진 채로 지금까지처럼 돈다.
        assertThat(classifyWith(REAL_RESPONSE).splitVersion()).isNull();
    }

    @Test
    @DisplayName("라벨과 함께 split_version 을 실어 보낸다")
    void 분리_버전_보내기() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andExpect(jsonPath("$.split_version").value("split-v2"))
                .andExpect(jsonPath("$.labels['0']").value("findings"))
                .andRespond(withSuccess(REAL_RESPONSE, MediaType.APPLICATION_JSON));

        new HttpAiMemoClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .classify(new MemoRequest("메모", null, null,
                        Map.of("0", "findings"), null, null, "split-v2"));

        server.verify();
    }

    @Test
    @DisplayName("라벨이 없으면 split_version 을 보내지 않는다")
    void 분리_버전_안_보냄() {
        // 라벨이 없으면 AI 가 새로 나누고 새 이름을 돌려준다. 견줄 대상이 없는데 보내면
        // 방금 만들 값을 미리 단정하는 꼴이고, 규칙이 바뀐 직후 첫 분류가 409 로 막힌다.
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andExpect(jsonPath("$.split_version").doesNotExist())
                .andRespond(withSuccess(REAL_RESPONSE, MediaType.APPLICATION_JSON));

        new HttpAiMemoClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .classify(new MemoRequest("메모", null, null, null, null, null, "split-v2"));

        server.verify();
    }

    @Test
    @DisplayName("409 는 502 로 뭉개지 않고 '다시 정리해주세요'로 앱에 넘긴다")
    void 분리_규칙이_바뀜() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": "분리 규칙이 바뀌었습니다. 다시 분류해 주세요 (보낸 값 split-v1, 서버 split-v2)"}"""));

        HttpAiMemoClient client = new HttpAiMemoClient(
                builder.build(), new ObjectMapper(), new AiSigner("test-secret"));
        MemoRequest request = new MemoRequest("메모", null, null,
                Map.of("0", "findings"), null, null, "split-v1");

        // 502 로 뭉개면 앱이 "AI 가 죽었다"로 읽고 재시도만 한다. 다시 분류해야 풀린다.
        assertThatThrownBy(() -> client.classify(request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SPLIT_VERSION_CHANGED);

        // 한 번만 부른다. 같은 라벨로 다시 보내봐야 또 409 다.
        server.verify();
    }

    @Test
    @DisplayName("409 의 detail 을 로그로도 앱으로도 내보내지 않는다")
    void 분리_규칙_오류에_메모가_안_샌다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": "분리 규칙이 바뀌었습니다 — 허리 디스크 초기래요"}"""));

        HttpAiMemoClient client = new HttpAiMemoClient(
                builder.build(), new ObjectMapper(), new AiSigner("test-secret"));
        MemoRequest request = new MemoRequest("메모", null, null,
                Map.of("0", "findings"), null, null, "split-v1");

        // AI 가 detail 에 메모 조각을 실어 보낼 수 있다. 그대로 흘리면 증상이 앱 화면과
        // 로그로 샌다. 우리가 정한 문구만 나간다.
        assertThatThrownBy(() -> client.classify(request))
                .isInstanceOf(ApiException.class)
                .hasMessageNotContaining("허리 디스크");
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
                .classify(MemoRequest.classifyOnServer("메모", null, null));

        server.verify();
    }

    private MemoClassification classifyWith(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000/v1/postvisit/memo"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        return new HttpAiMemoClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .classify(MemoRequest.classifyOnServer("메모", LocalDate.of(2026, 9, 12), "OO정형외과"));
    }
}
