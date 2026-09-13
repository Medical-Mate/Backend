package com.jinryomate.backend.appointment.repository;

import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.auth.entity.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * 기간 조회. 월 뷰와 일자별이 같은 메서드를 쓴다 — 경계만 다르다.
     *
     * <p><b>양끝을 포함한다.</b> 날짜를 {@code DATE} 로 두면서 "다음 달 1일 00:00 미만"
     * 같은 요령이 필요 없어졌다. 9월이면 9월 1일부터 9월 30일까지다.
     *
     * <p>시각이 없는 일정은 뒤로 보낸다. 같은 날이면 시간이 정해진 것부터 보여주는 게
     * 자연스럽고, "시간 미정" 은 아직 할 일이 남은 쪽이다.
     *
     * <p><b>카드를 함께 가져온다.</b> 응답에 카드 제목을 싣는데 그냥 두면 일정 수만큼
     * 쿼리가 더 나간다(N+1). 한 달 치를 한 번에 그리는 화면이라 그대로 눈에 띈다.
     */
    @Query("""
            select a from Appointment a
            left join fetch a.cards
            where a.user.id = :userId
              and a.scheduledOn between :from and :to
            order by a.scheduledOn asc, a.scheduledTime asc nulls last, a.id asc
            """)
    List<Appointment> findInRange(@Param("userId") Long userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    /** 홈의 "다가오는 일정". 아직 안 지났고 취소되지 않은 것 중 가장 가까운 순. */
    @Query("""
            select a from Appointment a
            left join fetch a.cards
            where a.user.id = :userId
              and a.status = :status
              and a.scheduledOn >= :from
            order by a.scheduledOn asc, a.scheduledTime asc nulls last, a.id asc
            """)
    List<Appointment> findUpcoming(@Param("userId") Long userId,
                                   @Param("status") Appointment.Status status,
                                   @Param("from") LocalDate from);

    /**
     * 이 카드들을 가져가기로 한 일정을 찾는다. 카드를 지울 때 연결을 끊는 데 쓴다.
     *
     * <p>일정 자체는 남는다 — 카드 없이도 성립한다.
     */
    @Query("select distinct a from Appointment a join a.cards c where c.id in :cardIds")
    List<Appointment> findAllByCardIdIn(@Param("cardIds") Collection<Long> cardIds);

    void deleteAllByUser(User user);
}
