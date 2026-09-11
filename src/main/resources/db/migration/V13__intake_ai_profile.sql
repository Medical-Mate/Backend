-- 추출을 폰에서 돌리는지 서버에서 돌리는지.
--
-- 세션 시작에 한 번 정해지고 턴마다 바뀌지 않아서 세션에 둔다. 턴 요청의
-- extraction 은 저장하지 않는다 — 증상 텍스트가 든 중간 산출물이고, 카드에 근거가
-- 이미 남으므로 우리가 또 들고 있을 이유가 없다.
ALTER TABLE intake_sessions ADD COLUMN ai_profile VARCHAR(16) NOT NULL DEFAULT 'SERVER';

-- CHECK 를 걸지 않는다. 값 집합은 AI 계약이 정하는 것이라 늘어나면 우리 기동이 막힌다.
