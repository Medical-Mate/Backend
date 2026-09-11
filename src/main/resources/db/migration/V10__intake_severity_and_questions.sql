-- 증상 정리 3·4단계 — 통증 강도와 의사에게 물어볼 것.
--
-- 흐름은 네 단계다: 부위 짚기 → 문답 → 통증 강도 → 의사에게 물어볼 것.
-- 문답까지는 있었는데 뒤 둘을 받을 자리가 없었다.
--
-- 카드가 아니라 세션에 붙인다. 3·4단계는 카드가 만들어지기 전이고,
-- 카드는 이 값들을 읽어서 담는다. 카드에 두면 문답 중간에 나간 사람에게도
-- 빈 카드가 생긴다.

-- 통증 강도. 와이어프레임 1d 는 1~5 서열척도다 (NRS 0~10 이 아니다).
ALTER TABLE intake_sessions
    ADD COLUMN severity_level INTEGER;

-- "꽤 아파요" 같은 표시 문구. 앱이 보낸다.
--
-- 서버가 들고 있으면 문구를 바꿀 때마다 배포해야 하고, 이건 디자인 카피라
-- 우리 것이 아니다. AI 에 selections 로 넘길 때 "3 (꽤 아파요)" 를 조립하는 데 쓴다.
ALTER TABLE intake_sessions
    ADD COLUMN severity_label VARCHAR(40);

-- 1~5 밖의 값은 애플리케이션에서도 막지만 여기서도 막는다.
-- 척도가 바뀌면 이 제약과 검증을 함께 고친다.
ALTER TABLE intake_sessions
    ADD CONSTRAINT ck_intake_sessions_severity_level
        CHECK (severity_level IS NULL OR (severity_level BETWEEN 1 AND 5));

-- 의사에게 물어볼 것. 화면 1i 는 최대 3개이고 번호(①②③)가 붙는다.
--
-- 순서가 화면에 보이므로 seq 로 고정한다. 목록을 통째로 다시 받는 방식이라
-- 갱신할 때마다 지우고 다시 넣는다 — 개수가 3개라 비용이 없다.
CREATE TABLE intake_session_questions (
    session_id BIGINT       NOT NULL,
    seq        INTEGER      NOT NULL,
    text       VARCHAR(200) NOT NULL,

    CONSTRAINT fk_intake_session_questions_session
        FOREIGN KEY (session_id) REFERENCES intake_sessions (id),
    CONSTRAINT uk_intake_session_questions_seq UNIQUE (session_id, seq)
);

CREATE INDEX idx_intake_session_questions_session ON intake_session_questions (session_id);
