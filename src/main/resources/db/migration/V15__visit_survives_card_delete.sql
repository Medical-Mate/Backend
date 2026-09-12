-- 카드를 지워도 진료 기록은 남긴다.
--
-- 카드 삭제가 그 카드에 달린 기록까지 지우고 있었다. 근거가 "기록은 그 카드에 대한 것이라
-- 홀로 남을 수 없다"였는데 틀렸다.
--
-- 카드는 진료 **전에** 만든 준비물이고, 기록은 진료에서 **실제로 들은 것**이다. 준비물을
-- 지웠다고 의사에게 들은 말이 사라지면 안 된다. 재방문 이력 화면(1j-3-R)도 기록이 카드보다
-- 오래 남는 것을 전제로 그려져 있다.
--
-- 게다가 기록 목록의 삭제가 카드 삭제로 대신 나가고 있어서, 기록 한 건을 지우려던 사용자가
-- 카드까지 잃었다.

ALTER TABLE visit_records ALTER COLUMN card_id DROP NOT NULL;

-- 카드가 지워진 뒤에도 기록 목록이 줄 제목을 그릴 수 있게 남긴다.
--
-- 카드가 살아 있는 동안은 카드의 현재 제목을 쓴다(환자가 고치면 따라가야 한다). 카드가
-- 사라진 뒤에만 이 값으로 내려간다 — displayTitle() 과 같은 방식이다.
ALTER TABLE visit_records ADD COLUMN card_title VARCHAR(120);

-- 이미 있는 기록의 제목을 채워 둔다. 지금은 스모크 테스트 것뿐이지만,
-- 나중에 카드가 지워지면 제목 없는 줄이 남는다.
UPDATE visit_records v
SET card_title = COALESCE(c.title, c.chief_complaint)
FROM briefing_cards c
WHERE v.card_id = c.id;
