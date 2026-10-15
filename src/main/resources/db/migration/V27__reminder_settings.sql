-- 내 정보(1s-1) 리디자인으로 진료 알림이 둘로 는다. #172.
--
-- 예약은 지금처럼 앱이 로컬로 한다. 서버가 보낼 것은 없다.
--
--   알림              | 시각을 적은 일정 | 시간 미정 일정
--   진료 전 알림      | 진료 24시간 전   | 전날, 사용자가 정한 시각
--   진료 후 기록 알림 | 진료 2시간 뒤    | 당일, 사용자가 정한 시각
--
-- 시각을 적은 일정은 규칙이 고정이라 저장할 것이 없다. 서버에 둘 것은 토글 둘과
-- 시간 미정 일정에 쓸 시각 둘이다. visit_reminder_enabled(V18)와 같은 이유로 계정에
-- 붙인다 — 기기를 바꾸거나 앱을 다시 깔아도 그 사람이 정한 대로 가야 한다.
--
-- 시각은 시간대 없이 TIME 이다. 한국 시각으로 읽는다. 일정의 scheduled_time 과 같다.
--
-- NOT NULL + 기본값이라 이미 있는 회원도 켜짐 · 19:00 으로 채워진다. 진료 전 알림과
-- 같은 이유로 기본은 켜짐이다 — 명시적으로 끄기 전까지는 알린다.
--
-- "헤이 메이트로 부르기" 토글은 여기 없다. 켜면 그 기기에 음성 모델을 받고, 기기
-- 사양과 마이크 권한에 묶여 있어 계정을 따라가면 다른 기기에서 맞지 않는다.
ALTER TABLE users ADD COLUMN record_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN visit_reminder_time     TIME    NOT NULL DEFAULT '19:00';
ALTER TABLE users ADD COLUMN record_reminder_time    TIME    NOT NULL DEFAULT '19:00';
