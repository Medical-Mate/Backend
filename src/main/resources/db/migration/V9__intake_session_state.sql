-- 문답 턴 처리에 필요한 두 컬럼.
--
-- AI 서버는 무상태다. 턴마다 {state, utterance} 를 보내고 돌아온 state 로 덮어쓴다.
-- 그 state 를 들고 있을 자리가 없어서 답변을 보낼 수가 없었다.
--
-- JSONB 가 아니라 TEXT 다. 우리는 이 값을 절대 해석하지 않는다 —
-- AI 계약서(api-previsit.md)가 "불투명하게 보관, 내부 필드에 의존하지 않는다"고
-- 명시했다. JSONB 로 두면 인덱스를 걸거나 들여다보고 싶어지는데, 그 순간
-- AI 쪽 구조 변경이 우리를 깨뜨린다.
ALTER TABLE intake_sessions
    ADD COLUMN ai_state TEXT;

-- 문답이 왜 끝났는지. AI 가 돌려주는 값을 그대로 담는다.
--   stop      환자가 그만두겠다고 함
--   complete  8축이 모두 닫힘
--   max_turns 턴 상한 도달
--   budget    토큰 예산 도달
--
-- CHECK 를 걸지 않는다. AI 가 새 사유를 추가하면 우리 쪽 기동이 막힌다 —
-- 종료 사유가 하나 늘었다고 서비스가 죽을 이유가 없다.
ALTER TABLE intake_sessions
    ADD COLUMN end_reason VARCHAR(16);

-- 대화 턴 상한을 6 에서 20 으로 올린다.
--
-- 6 은 근거가 없어진 값이었다. AI 계약의 안전장치가 20 턴이고, 정상 문답은 6턴이다
-- (시작·느낌·경과·악화완화·퍼짐·동반).
--
-- 와이어프레임의 "2 / 4" 와 헷갈리기 쉬운데 그건 화면 단계다 —
-- 부위 짚기 → 문답 → 통증 강도 → 의사에게 물어볼 것. 이 컬럼은 그 2단계 안에서
-- 주고받는 대화 턴이다. 화면 단계 진행도는 앱이 안다(AI#7 확인).
ALTER TABLE intake_sessions
    ALTER COLUMN progress_total SET DEFAULT 20;

-- 이미 진행 중인 세션은 건드리지 않는다. 문답 도중에 상한이 바뀌면
-- 화면의 진행 바가 뒤로 가는 것처럼 보인다.
