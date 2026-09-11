-- 부위를 코드 배열에서 온톨로지 노드 하나 + 좌우로 바꾼다.
--
-- 배열이었던 것은 계약을 받기 전에 지은 것이다. AI 는 세션 시작에 부위를 하나만 받는다
-- (site_node_id). 부위 여러 개는 AI 저장소 #8 에서 팀 결정 대기 중이라, 정해지면 그때
-- 양쪽 계약을 함께 고친다.
--
-- 코드 형식도 바뀐다. 우리가 쓰던 hand_finger_joint_R 은 스텁을 만들며 지어낸 것이고,
-- 실제는 body-map 의 ANC:001 · SUR:032 다.

-- 좌우를 코드에 박지 않는다. SUR:051(어깨) 하나가 좌우를 다 덮고 구분은 side 가 한다.
-- 코드에 박으면 노드가 두 배로 늘고, 좌우가 없는 부위(머리·배)와 규칙이 갈린다.
ALTER TABLE intake_sessions ADD COLUMN site_node_id VARCHAR(40);
ALTER TABLE intake_sessions ADD COLUMN side VARCHAR(8);

-- 기존 값은 옮기지 않는다. 지어낸 형식이라 옮길 대응이 없고, 운영 데이터도
-- 스모크 테스트로 만든 세션 하나뿐이다.
DROP TABLE IF EXISTS intake_session_sites;

-- side 에 CHECK 를 걸지 않는다. 값 집합(left·right·both)은 AI 계약이 정하는 것이라
-- 늘어나면 우리 기동이 막힌다. 엔티티 enum 과 BodyMap 검증이 앞에서 거른다.
