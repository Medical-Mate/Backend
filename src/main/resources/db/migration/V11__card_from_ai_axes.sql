-- 브리핑 카드를 AI 계약의 실제 모양으로 바꾼다.
--
-- 기존 본문(onset·pattern·site·medications·allergies 다섯 칸)은 계약을 받기 전에
-- 와이어프레임만 보고 지은 것이다. 실제로 AI 가 주는 것은 SOCRATES 8축이고,
-- 축마다 status·value·evidence 를 갖는다. 칸이 다섯이면 세 축을 버리게 된다.
--
-- 복용약·알레르기는 카드 본문이 아니라 프로필에 있다(health_profiles). 카드에 또 두면
-- 같은 사실이 두 곳에 남아 어긋난다. 카드 화면에는 프로필 값을 얹어 보여준다.

-- 세션이 마지막으로 받은 AI 카드. 카드는 매 턴 응답에 딸려 오지만 턴마다 행을 만들지는
-- 않는다 — 그러면 버전 체인이 의미를 잃는다. 여기 보관해뒀다가 카드를 만들 때 쓴다.
ALTER TABLE intake_sessions ADD COLUMN ai_card TEXT;

-- --- 카드 본문 교체 ---

ALTER TABLE briefing_cards DROP COLUMN onset_status;
ALTER TABLE briefing_cards DROP COLUMN onset_text;
ALTER TABLE briefing_cards DROP COLUMN pattern_status;
ALTER TABLE briefing_cards DROP COLUMN pattern_text;
ALTER TABLE briefing_cards DROP COLUMN site_status;
ALTER TABLE briefing_cards DROP COLUMN site_text;
ALTER TABLE briefing_cards DROP COLUMN site_codes;
ALTER TABLE briefing_cards DROP COLUMN medications_status;
ALTER TABLE briefing_cards DROP COLUMN medications;
ALTER TABLE briefing_cards DROP COLUMN allergies_status;
ALTER TABLE briefing_cards DROP COLUMN allergies_text;
ALTER TABLE briefing_cards DROP COLUMN suggested_department;
ALTER TABLE briefing_cards DROP COLUMN evidence;

-- 환자가 말한 그대로. 줄이지 않는다 — 줄이는 순간 환자 말이 아니다.
ALTER TABLE briefing_cards ADD COLUMN chief_complaint TEXT;

-- title 은 AI 가 부위 + 기간을 결정론으로 조합해 만든다. 아직 내려오지 않아 NULL 을 허용한다.
-- 20자 이내가 보장된다고 했지만 조합 규칙이 확정 전이라 여유를 둔다.
ALTER TABLE briefing_cards ALTER COLUMN title DROP NOT NULL;
ALTER TABLE briefing_cards ALTER COLUMN title TYPE VARCHAR(40);

-- 진료과는 우리가 판정하지 않는다. AI 가 부위 노드 속성으로 주는 배열을 그대로 담는다.
-- 배열인 이유 — 한 부위에 여러 과가 붙고, 하나로 좁히는 순간 그게 추천이 된다.
ALTER TABLE briefing_cards ADD COLUMN department_guidance JSONB;

-- "팀 결정 2026-09-04, 의료인 자문 확인 전". 화면에 함께 보여야 해서 같이 저장한다 —
-- 인용이 아니라는 것을 숨기지 않는 것이 이 값의 목적이다.
ALTER TABLE briefing_cards ADD COLUMN department_guidance_source VARCHAR(120);

-- 축 밖으로 새는 환자 말. 버리면 "타이레놀 먹었어요" 같은 게 사라진다.
ALTER TABLE briefing_cards ADD COLUMN patient_notes JSONB;

-- 의료인 자문 전 자리. 지금은 빈 배열로 온다.
ALTER TABLE briefing_cards ADD COLUMN red_flags JSONB;

-- 8축 중 몇 개가 찼는지. 앱이 "조금 더 여쭤볼까요"를 띄우는 근거다.
ALTER TABLE briefing_cards ADD COLUMN completeness REAL;
ALTER TABLE briefing_cards ADD COLUMN minimally_complete BOOLEAN;

-- --- 추적성 ---
-- pipeline_version 하나로는 무엇이 만들었는지 못 짚는다. AI 는 프롬프트 판과 모델을
-- 따로 준다(provenance). 프롬프트만 같고 모델이 바뀌면 결과가 달라진다.
ALTER TABLE briefing_cards RENAME COLUMN pipeline_version TO prompt_version;
ALTER TABLE briefing_cards ADD COLUMN model_id VARCHAR(64);
ALTER TABLE briefing_cards ADD COLUMN ontology_snapshot VARCHAR(32);

-- --- 축 ---
-- 컬럼 여덟 벌로 펼치지 않는다. 축이 늘거나 줄면 그때마다 마이그레이션이 필요하고,
-- "축마다 같은 세 값을 갖는다"는 구조가 스키마에 드러나지 않는다.
CREATE TABLE briefing_card_axes (
    card_id  BIGINT       NOT NULL,
    axis     VARCHAR(32)  NOT NULL,
    status   VARCHAR(16)  NOT NULL,
    value    VARCHAR(200),
    -- 환자 발화 원문. AI 가 배열로 준다 — 한 축이 여러 발화에서 채워질 수 있다.
    evidence JSONB,
    -- ai_extraction · selection · patient_edit.
    -- 환자가 S3 에서 고친 값을 의사가 구별할 수 있어야 한다.
    -- AI 가 아직 안 내려주므로 NULL 을 허용한다.
    source   VARCHAR(16),
    CONSTRAINT pk_briefing_card_axes PRIMARY KEY (card_id, axis),
    CONSTRAINT fk_briefing_card_axes_card FOREIGN KEY (card_id) REFERENCES briefing_cards (id)
);

-- 축 이름에 CHECK 를 걸지 않는다. AI 가 축을 하나 늘리면 우리 기동이 막힌다.
-- 모르는 축이 와도 저장하고, 화면에 무엇을 보여줄지는 앱이 정한다.
