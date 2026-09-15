package com.jinryomate.backend.visit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 같은 값을 <b>두 계약이 다른 이름으로</b> 부르는 자리.
 *
 * <p>AI 계약은 {@code split_version}, 우리는 {@code splitVersion} 이다. 앱이 AI 문서를
 * 보고 뱀 표기로 보내면 Jackson 이 모르는 필드로 보고 <b>조용히 버린다</b> — 400 도 안 나고
 * 200 이 나간다.
 *
 * <p>그러면 <b>409 검사가 통째로 꺼진다.</b> 예전 문장 번호가 새 문장에 붙어 조용히 어긋난
 * 카드가 저장되는데, 그게 이 필드를 만든 이유와 정확히 같은 사고다. 이름 때문에 방어가
 * 꺼지게 두지 않는다.
 *
 * <p>부위에서 실제로 같은 일이 났다 — {@code site_node_id} 로 온 값이 떨어져서 문답이
 * 처음부터 다시 돌았다.
 */
class ClassifyMemoRequestNamingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("splitVersion 으로 보내면 읽는다")
    void 낙타_표기() throws Exception {
        assertThat(parse("""
                {"memo": "혈액검사 했어요", "splitVersion": "split-v4"}""").splitVersion())
                .isEqualTo("split-v4");
    }

    @Test
    @DisplayName("split_version 으로 보내도 읽는다")
    void 뱀_표기() throws Exception {
        // 이게 없으면 값이 null 이 되고, 서버가 검사를 건너뛴 채 200 을 낸다.
        assertThat(parse("""
                {"memo": "혈액검사 했어요", "split_version": "split-v4"}""").splitVersion())
                .isEqualTo("split-v4");
    }

    @Test
    @DisplayName("안 보내면 그대로 비어 있다")
    void 안_보냄() throws Exception {
        // 안 보내는 것은 정상이다 — 계약이 "없으면 검사하지 않는다" 이고, 앱이 붙이기
        // 전까지 지금처럼 동작해야 한다. 별칭이 그걸 바꾸지 않는다.
        assertThat(parse("""
                {"memo": "혈액검사 했어요"}""").splitVersion()).isNull();
    }

    private ClassifyMemoRequest parse(String json) throws Exception {
        return objectMapper.readValue(json, ClassifyMemoRequest.class);
    }
}
