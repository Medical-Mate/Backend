-- 재방문 날짜에 원문과 "대략" 여부를 함께 남긴다.
--
-- V17 에서 follow_up_date 만 만들었는데, 시안 1q-1 은 "2주 뒤 (9월 27일 전후)" 로 찍는다.
-- 날짜만 남기면 "2주 뒤" 도 "전후" 도 못 그린다.
--
-- AI 계약(docs/api-postvisit.md)이 셋을 함께 준다:
--   {"text": "2주 뒤", "date": "2026-09-27", "approximate": true, "basis": "..."}
--
-- basis("visit_date 2026-09-13 + 14d")는 싣지 않는다. 화면에 쓰는 곳이 없는 내부 계산 근거다.

-- 환자가 말한 그대로. "2주 뒤" · "다음 주 화요일"
ALTER TABLE visit_records ADD COLUMN follow_up_text VARCHAR(60);

-- true 면 화면에 "전후" 를 붙인다. "2주 뒤" 는 날짜가 아니라 범위다.
-- 값이 없으면 재방문 자체가 없는 것이라 NULL 을 허용한다.
ALTER TABLE visit_records ADD COLUMN follow_up_approximate BOOLEAN;
