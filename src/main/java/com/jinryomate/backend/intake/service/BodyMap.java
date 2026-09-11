package com.jinryomate.backend.intake.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 부위 마스터. AI 저장소의 {@code docs/examples/body-map.json} 을 그대로 복사해 둔 것이다.
 *
 * <p><b>기동할 때 AI 를 부르지 않는다.</b> {@code GET /v1/ontology/body-map} 이 단일 원천이지만
 * 거기서 받아오면 AI 가 떠 있어야 백엔드가 뜬다. 우리는 AI 없이도 기동돼야 한다 —
 * 지금 스텁으로 돌 수 있는 이유가 그것이다.
 *
 * <p>폴링도 하지 않는다. <b>바뀌면 AI 담당이 알린다</b>는 것이 합의다. 우리가 들고 있는 판은
 * {@link #getSnapshot()} 으로 확인한다.
 *
 * <p>여기서 거르는 이유 — AI 도 같은 검증을 하지만, 모르는 부위를 그대로 넘기면
 * <b>문답을 시작한 뒤에</b> 422 가 돌아온다. 앱은 이미 화면을 넘긴 뒤다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BodyMap {

    private static final String RESOURCE = "ontology/body-map.json";

    /** 좌우를 가질 수 있는 노드의 {@code laterality} 값. */
    private static final String LEFT_RIGHT = "left_right";

    private final ObjectMapper objectMapper;

    /** 이 파일이 어느 판인지. AI 응답의 같은 이름 값과 대조한다. */
    @Getter
    private String snapshot;

    private Map<String, Node> nodes = Map.of();

    /**
     * @param label       사람이 읽는 이름. 예: {@code 아랫배}
     * @param lateralized 좌우를 붙일 수 있는지
     */
    public record Node(String id, String label, boolean lateralized) {}

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            snapshot = root.path("ontology_snapshot").asText(null);

            Map<String, Node> loaded = new LinkedHashMap<>();
            for (JsonNode anchor : root.path("anchors")) {
                put(loaded, anchor);
                for (JsonNode zone : anchor.path("zones")) {
                    put(loaded, zone);
                }
            }
            nodes = Map.copyOf(loaded);

            log.info("부위 마스터 로드 snapshot={} nodes={}", snapshot, nodes.size());
        } catch (Exception e) {
            // 부위 검증 없이 뜨면 모르는 코드가 AI 까지 가서 문답 도중에 422 가 된다.
            // 리소스는 저장소에 같이 들어 있으므로 여기서 실패하면 빌드가 잘못된 것이다.
            throw new IllegalStateException("부위 마스터를 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    private void put(Map<String, Node> into, JsonNode json) {
        String id = json.path("id").asText(null);
        if (id == null) {
            return;
        }
        into.put(id, new Node(
                id,
                json.path("label").asText(null),
                LEFT_RIGHT.equals(json.path("laterality").asText())));
    }

    public boolean has(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public Node get(String nodeId) {
        return nodes.get(nodeId);
    }

    public int size() {
        return nodes.size();
    }
}
