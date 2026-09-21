-- 웹 데모 이벤트.
--
-- **한시입니다.** 심사가 끝나면 demo 패키지와 함께 스키마째 지웁니다.
--
-- 왜 별도 스키마인가 — Grafana 가 이 DB 를 봅니다. public 에 두고 앱 계정을
-- 그대로 꽂으면 Grafana 가 뚫렸을 때 card·visit·profile 까지 열립니다. 스키마를
-- 나누면 읽기 전용 롤에 analytics 만 줄 수 있습니다.
--
-- 롤 생성은 여기 두지 않습니다. 비밀번호가 환경마다 달라 마이그레이션에 박으면
-- 저장소에 남습니다. 절차는 docs/analytics-grafana.md 에 있습니다.
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.demo_event (
    id          BIGSERIAL    PRIMARY KEY,

    -- 이벤트 이름. 서버 화이트리스트(DemoEventCatalog) 안의 값만 들어옵니다.
    event       VARCHAR(48)  NOT NULL,

    -- 브라우저가 찍은 시각. 오프셋을 포함해 받습니다 — UTC 로만 저장하면
    -- "저녁에 이탈이 많다" 같은 시간대 분석이 틀어집니다.
    occurred_at TIMESTAMPTZ  NOT NULL,

    -- 브라우저 세션마다 새로 만드는 난수. **문답 세션 id 가 아닙니다.**
    -- 같으면 이벤트가 카드와 이어져 누가 무엇을 말했는지가 복원됩니다.
    session_id  VARCHAR(32)  NOT NULL,

    seq         INTEGER      NOT NULL,
    surface     VARCHAR(8)   NOT NULL,
    build       VARCHAR(32),

    -- 이벤트마다 속성이 달라 JSONB 입니다. 무엇이 들어올 수 있는지는
    -- 서버가 정하고, 목록 밖의 키는 400 으로 거부합니다.
    props       JSONB        NOT NULL DEFAULT '{}'::jsonb,

    -- 서버가 받은 시각. occurred_at 과 크게 벌어지면 시계가 틀린 기기입니다.
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 대시보드가 실제로 던지는 쿼리 모양입니다.
CREATE INDEX idx_demo_event_occurred ON analytics.demo_event (occurred_at);
CREATE INDEX idx_demo_event_name_time ON analytics.demo_event (event, occurred_at);

-- 한 세션의 흐름을 순서대로 따라가는 용도. 이탈 구간을 볼 때 씁니다.
CREATE INDEX idx_demo_event_session ON analytics.demo_event (session_id, seq);

COMMENT ON SCHEMA analytics IS '웹 데모 사용 이벤트. 심사 종료 후 삭제한다.';
COMMENT ON TABLE analytics.demo_event IS '환자 데이터가 아니다. 원문·식별자를 담지 않는다.';
