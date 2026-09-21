package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.intake.entity.IntakeTurnAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 기록은 쓰기만 합니다.
 *
 * <p>읽는 쪽은 심사가 끝난 뒤 <b>내보내기 한 번</b>이고, 그때는 세션 id 기준 JSONL 로
 * 뽑습니다. 화면도 조회 API 도 만들지 않습니다 — AI 트랙이 파일 하나면 된다고 했고,
 * 조회 경로를 열면 환자 발화가 나가는 문이 하나 더 생깁니다.
 *
 * <p>삭제는 {@link com.jinryomate.backend.intake.entity.IntakeSession} 의 cascade 가
 * 합니다. 탈퇴가 세션을 지우면 함께 사라집니다.
 */
public interface IntakeTurnAuditRepository extends JpaRepository<IntakeTurnAudit, Long> {
}
