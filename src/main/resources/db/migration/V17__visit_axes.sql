-- 진료 후 기록을 세 칸 고정에서 축 목록으로 바꾼다. 카드가 V11 에서 한 것과 같다.
--
-- what_was_done · result · prescription 세 칸으로는 시안 1q-1 을 그릴 수 없다.
-- 시안은 브리핑 카드와 같은 모양의 카드 한 장이고, AI 가 나눈 항목을 그대로 보여준다.
-- 항목 수가 가변이다 — "소견"을 못 찾으면 그 줄이 없고, 다른 항목을 찾으면 그 줄이 생긴다.
--
-- AI 의 /v1/postvisit/memo 가 네 축을 준다:
--   findings(소견) · tests(검사) · medication_instructions(약) · follow_up(재방문)
-- 응답 모양이 브리핑 카드의 axes 와 같아서 앱이 그리는 코드를 나눠 쓴다.

CREATE TABLE visit_record_axes (
    visit_id BIGINT      NOT NULL,
    axis     VARCHAR(32) NOT NULL,
    status   VARCHAR(16) NOT NULL,
    value    VARCHAR(200),
    -- 메모 원문 구간. AI 가 배열로 준다 — 한 축이 여러 문장에서 채워질 수 있다.
    evidence JSONB,
    -- ai_extraction · patient_edit. 환자가 1q-1-E 에서 고친 값을 구별해야 한다.
    source   VARCHAR(16),
    CONSTRAINT pk_visit_record_axes PRIMARY KEY (visit_id, axis),
    CONSTRAINT fk_visit_record_axes_visit
        FOREIGN KEY (visit_id) REFERENCES visit_records (id) ON DELETE CASCADE
);

-- 축 이름에 CHECK 를 걸지 않는다. 카드와 같은 이유다 — AI 가 축을 하나 늘리면 기동이 막힌다.

-- 재방문 날짜. 1q-1 의 네 줄 중 하나인데 지금까지 실을 자리가 없어서
-- 환자가 "2주 뒤"를 적어도 저장하면 사라졌다.
--
-- 캘린더 일정은 여기서 만들지 않는다. 앱이 이 값을 읽어
-- POST /api/me/appointments 를 부른다 — 환자가 확인하고 등록하는 흐름(1r-2-A)이고,
-- AI 가 날짜를 잘못 뽑아도 조용히 일정이 생기지 않는다.
ALTER TABLE visit_records ADD COLUMN follow_up_date DATE;

-- 어느 축에도 안 들어간 문장. 버리지 않는다.
ALTER TABLE visit_records ADD COLUMN patient_notes JSONB;

-- 무엇이 이 기록을 나눴는지. 프롬프트가 같아도 모델이 바뀌면 결과가 달라진다.
-- 환자가 직접 적은 기록이면 NULL 이다.
ALTER TABLE visit_records ADD COLUMN prompt_version VARCHAR(40);
ALTER TABLE visit_records ADD COLUMN model_id VARCHAR(80);

-- 기존 세 칸을 축으로 옮긴다.
--
-- 이름이 정확히 겹치지는 않는다. what_was_done("혈액검사(류마티스 인자 포함)")이 tests,
-- prescription("나프록센 500mg·하루 2번 식후")이 medication_instructions 인 것은 분명하고,
-- result("3일 뒤 확인")는 findings 가 가장 가깝다.
--
-- source 는 patient_edit 이다. 이 값들은 AI 가 나눈 것이 아니라 환자가 세 칸에 직접
-- 적은 것이다. ai_extraction 으로 넣으면 의사 화면에서 출처가 거짓말이 된다.
INSERT INTO visit_record_axes (visit_id, axis, status, value, evidence, source)
SELECT id, 'findings', 'FILLED', LEFT(result, 200), '[]'::jsonb, 'PATIENT_EDIT'
FROM visit_records WHERE result IS NOT NULL AND result <> '';

INSERT INTO visit_record_axes (visit_id, axis, status, value, evidence, source)
SELECT id, 'tests', 'FILLED', LEFT(what_was_done, 200), '[]'::jsonb, 'PATIENT_EDIT'
FROM visit_records WHERE what_was_done IS NOT NULL AND what_was_done <> '';

INSERT INTO visit_record_axes (visit_id, axis, status, value, evidence, source)
SELECT id, 'medication_instructions', 'FILLED', LEFT(prescription, 200), '[]'::jsonb, 'PATIENT_EDIT'
FROM visit_records WHERE prescription IS NOT NULL AND prescription <> '';

ALTER TABLE visit_records DROP COLUMN what_was_done;
ALTER TABLE visit_records DROP COLUMN result;
ALTER TABLE visit_records DROP COLUMN prescription;
