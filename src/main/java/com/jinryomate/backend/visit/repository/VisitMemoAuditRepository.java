package com.jinryomate.backend.visit.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.visit.entity.VisitMemoAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 기록은 쓰기만 합니다.
 *
 * <p>읽는 쪽은 심사가 끝난 뒤 <b>내보내기 한 번</b>입니다. 화면도 조회 API 도 만들지
 * 않습니다 — 조회 경로를 열면 메모 원문이 나가는 문이 하나 더 생깁니다.
 *
 * <p>{@link #deleteAllByUser} 는 탈퇴가 부릅니다. 세션의 자식인 문답 감사 기록과 달리
 * 얹힐 cascade 가 없어서, {@code AuthService.deleteAllData} 에 줄이 하나 더 있습니다.
 */
public interface VisitMemoAuditRepository extends JpaRepository<VisitMemoAudit, Long> {

    void deleteAllByUser(User user);
}
