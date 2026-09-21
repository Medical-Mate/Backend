package com.jinryomate.backend.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 웹 데모 이벤트 한 줄.
 *
 * <p><b>환자 데이터가 아닙니다.</b> 증상·메모 원문, 검색어, 복용약 같은 값은 담지 않고
 * 숫자와 닫힌 목록의 값만 들어옵니다. 무엇이 들어올 수 있는지는
 * {@link com.jinryomate.backend.demo.service.DemoEventCatalog} 가 정합니다.
 *
 * <p><b>{@code sessionId} 는 문답 세션 id 가 아닙니다.</b> 브라우저 세션마다 만드는 별도
 * 난수입니다. 같은 값을 쓰면 이벤트가 카드와 이어져, 원문을 한 글자도 안 담아도
 * "이 사람이 무엇을 말했나"가 복원됩니다.
 *
 * <p><b>한시입니다.</b> 심사가 끝나면 {@code demo} 패키지와 함께 스키마째 지웁니다.
 */
@Entity
@Table(name = "demo_event", schema = "analytics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 48)
    private String event;

    /** 브라우저가 찍은 시각. 오프셋을 포함해 받습니다. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 8)
    private String surface;

    @Column(length = 32)
    private String build;

    /**
     * 이벤트마다 다른 속성.
     *
     * <p>키와 값 모두 화이트리스트를 통과한 것만 들어옵니다. 클라이언트가 보낸 것을
     * 그대로 담으면 언젠가 증상 텍스트가 섞입니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> props;

    /** 서버가 받은 시각. {@code occurredAt} 과 크게 벌어지면 시계가 틀린 기기입니다. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private DemoEvent(String event, Instant occurredAt, String sessionId, int seq,
                      String surface, String build, Map<String, Object> props) {
        this.event = event;
        this.occurredAt = occurredAt;
        this.sessionId = sessionId;
        this.seq = seq;
        this.surface = surface;
        this.build = build;
        this.props = props;
        this.receivedAt = Instant.now();
    }

    public static DemoEvent of(String event, Instant occurredAt, String sessionId, int seq,
                               String surface, String build, Map<String, Object> props) {
        return new DemoEvent(event, occurredAt, sessionId, seq, surface, build,
                props == null ? Map.of() : props);
    }
}
