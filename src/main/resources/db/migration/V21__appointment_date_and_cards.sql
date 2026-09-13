-- 일정은 날짜만 필수다. 그리고 카드를 여러 장 붙인다.

-- 1. 날짜와 시각을 나눈다.
--
-- 시안 1r-2-A 는 "9월 26일 (토) / 재방문 예정 / 시간 정하고 확정하기" 다. 날짜는 아는데
-- 시각은 아직 모르는 상태가 화면에 있는데, scheduled_at TIMESTAMPTZ NOT NULL 로는
-- 그걸 표현할 수 없다. 00:00 으로 넣으면 화면에 "오전 12:00" 이 찍힌다.
--
-- 시간대 계산이 사라지는 게 덤이다. 지금 월·일자 경계를 KST 로 고정해 자르고 있는데
-- (AppointmentService.ZONE), 그건 Instant 하나에 날짜와 시각이 섞여 있어서 필요했던
-- 일이다. "9월 26일"을 DATE 로 저장하면 시간대와 무관한 값이 된다.
ALTER TABLE appointments ADD COLUMN scheduled_on   DATE;
ALTER TABLE appointments ADD COLUMN scheduled_time TIME;

-- 기존 값은 KST 기준으로 쪼갠다. 지금까지 그 시간대로 잘라 왔으므로 화면과 어긋나지 않는다.
UPDATE appointments SET
    scheduled_on   = (scheduled_at AT TIME ZONE 'Asia/Seoul')::date,
    scheduled_time = (scheduled_at AT TIME ZONE 'Asia/Seoul')::time;

ALTER TABLE appointments ALTER COLUMN scheduled_on SET NOT NULL;

DROP INDEX IF EXISTS idx_appointments_user_scheduled;
CREATE INDEX idx_appointments_user_scheduled ON appointments (user_id, scheduled_on);

ALTER TABLE appointments DROP COLUMN scheduled_at;

-- 2. 어디서 만들어진 일정인지.
--
-- 시안 1r-2-A 가 "진료 후 기록에서 자동으로 만들었어요" 를 찍는다. 환자가 손으로 만든
-- 일정과 구별이 안 되면 저 문구를 못 그린다.
--
-- nullable 이 아니라 MANUAL 기본값이다. "모름"이라는 상태가 따로 없다 —
-- 재방문에서 온 게 아니면 사람이 만든 것이다.
ALTER TABLE appointments ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_origin
    CHECK (origin IN ('MANUAL', 'VISIT_FOLLOW_UP'));

-- 3. 일정 하나에 카드 여러 장.
--
-- 시안 1r-4-B 의 "가져갈 브리핑 카드" 가 체크박스이고 개수가 찍힌다. 지금은 card_id 가
-- 단수라 한 장만 붙는다.
--
-- 카드 쪽에 appointment_id 를 두는 방법도 있지만, 그러면 카드 하나가 일정 하나에만
-- 붙어서 같은 카드를 두 진료에 가져가는 경우를 막는다. 재방문에서 같은 카드를 이어
-- 쓰기로 한 것과 부딪힌다.
CREATE TABLE appointment_cards (
    appointment_id BIGINT NOT NULL,
    card_id        BIGINT NOT NULL,
    CONSTRAINT pk_appointment_cards PRIMARY KEY (appointment_id, card_id),
    CONSTRAINT fk_appointment_cards_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_cards_card
        FOREIGN KEY (card_id) REFERENCES briefing_cards (id)
);

CREATE INDEX idx_appointment_cards_card ON appointment_cards (card_id);

INSERT INTO appointment_cards (appointment_id, card_id)
SELECT id, card_id FROM appointments WHERE card_id IS NOT NULL;

ALTER TABLE appointments DROP COLUMN card_id;
