-- 복용약·기저질환을 카드에 싣는다. 알레르기와 같은 자리다.
--
-- V11 에서 카드 본문을 축 구조로 바꾸며 셋을 다 뺐고, V14 에서 알레르기만 되돌렸다.
-- 그때 "복용약은 목록이라 카드 한 장에 자리가 없고 시안의 카드에도 없다"고 적었는데,
-- 시안을 확인하지 않고 단정한 것이었다. 1e-1 의 KV 줄에 복용약·기저질환이 있다.
--
-- 알레르기와 같은 이유로 스냅샷이다. 카드는 참조가 아니라 만든 시점을 보존하는 것이고,
-- "이 카드를 만들 당시 이 환자가 먹던 약"이 진료실에서 맞는 값이다. 9월에 만든 카드를
-- 12월에 열었을 때 그때 복용 중인 약이 붙으면 안 된다.

-- 목록이라 jsonb 다. 프로필의 health_profile_medications 와 같은 값이지만,
-- 그쪽은 현재값이고 이쪽은 그 시점 사본이라 별개로 둔다.
ALTER TABLE briefing_cards ADD COLUMN medications JSONB;
ALTER TABLE briefing_cards ADD COLUMN medications_status VARCHAR(16);

ALTER TABLE briefing_cards ADD COLUMN conditions JSONB;
ALTER TABLE briefing_cards ADD COLUMN conditions_status VARCHAR(16);

-- V16 이전 카드는 채우지 않는다. V14(알레르기)와 같다. 지금의 프로필로 채우면
-- "그때 먹던 약"이 아니라 "지금 먹는 약"이 옛 카드에 박혀 스냅샷이 거짓말을 한다.
-- 그 시점 값은 어디에도 남아 있지 않으므로 status 를 비워 두는 편이 정직하다.
