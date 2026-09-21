package com.jinryomate.backend.demo.repository;

import com.jinryomate.backend.demo.entity.DemoEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이벤트는 쓰기만 합니다.
 *
 * <p>읽는 쪽은 Grafana 이고 SQL 로 직접 붙습니다 — 대시보드 패널마다 집계 모양이 달라
 * 여기에 조회 메서드를 만들 이유가 없습니다. 그래서 전용 읽기 롤을 따로 둡니다.
 */
public interface DemoEventRepository extends JpaRepository<DemoEvent, Long> {
}
