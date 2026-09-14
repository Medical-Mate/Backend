-- 카드 하나에 진료 기록 여럿.
--
-- 같은 증상으로 다시 가는 것이 진료의 보통 모양이다. 첫 진료에서 검사를 받고 두 번째에
-- 결과를 듣는데, 그때 들은 말은 첫 기록을 덮어쓸 것이 아니라 따로 쌓여야 한다.
-- 시안 1j-3-R (기록 상세 · 재방문 누적) 이 그 화면이다.
--
-- 지금은 UNIQUE 가 두 번째 기록을 막아서, 재방문을 적으려 하면 400 이 난다.
ALTER TABLE visit_records DROP CONSTRAINT IF EXISTS uk_visit_records_card;

-- 카드로 기록을 찾는 일이 이제 흔해진다(체인의 기록 모으기, 카드 상세의 최근 기록).
-- UNIQUE 가 사라지면서 그 자리에 있던 인덱스도 같이 없어졌다.
CREATE INDEX IF NOT EXISTS idx_visit_records_card ON visit_records (card_id);
