-- 마지막 AI 문장이 묻는 칸(AI 계약의 asking_axis, AI#156).
--
-- 앱이 폰 추출 프롬프트의 첫 줄 "질문한 항목: {label} (axis={key})" 로 쓴다(#157).
-- 앱은 이 값이 없으면 그 턴을 서버 추출로 보내므로, 응답에만 싣고 저장하지 않으면
-- 앱을 다시 켜 세션을 재조회할 때 발화가 말없이 서버(외부 LLM)로 샌다.
--
-- 값은 가공하지 않는다. state 를 열어 꺼낸 것이 아니라 AI 가 밖으로 낸 값이다.
--
-- TEXT 다. 길이를 걸면 AI 라벨이 길어지는 날 저장에서 터져 턴이 500 이 된다.
-- 칸을 더하기만 하므로 기존 행은 NULL 로 남고, 되돌리기는 DROP COLUMN 둘이다.
ALTER TABLE intake_sessions
    ADD COLUMN asking_axis_key   TEXT,
    ADD COLUMN asking_axis_label TEXT;
