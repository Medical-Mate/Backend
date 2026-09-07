package com.jinryomate.backend.appointment.repository;

import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.auth.entity.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * 기간 조회. 월 뷰와 일자별이 같은 메서드를 쓴다 — 경계만 다르다.
     *
     * <p>시작은 포함, 끝은 제외다. 월말 23:59:59.999 같은 값을 만들지 않아도 되고,
     * 다음 달 1일 00:00 을 끝으로 주면 경계가 정확히 맞는다.
     */
    List<Appointment> findAllByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            Long userId, Instant from, Instant to);

    /** 홈의 "다가오는 일정". 아직 안 지났고 취소되지 않은 것 중 가장 가까운 순. */
    List<Appointment> findAllByUserIdAndStatusAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            Long userId, Appointment.Status status, Instant from);

    void deleteAllByUser(User user);
}
