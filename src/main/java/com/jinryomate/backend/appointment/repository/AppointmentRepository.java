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
     * <b>진료 후 기록이 아직 없는 지난 일정</b> 가운데 가장 최근 날. 없으면 {@code null}.
     *
     * <p>홈의 "9월 12일 진료, 기록이 아직 없어요"에 쓴다. 날짜 하나면 문구를 만들 수 있어
     * 일정을 통째로 내지 않는다.
     *
     * <p><b>기록이 있는지는 날짜로 견준다.</b> 일정과 기록을 잇는 열쇠가 없어서다 — 기록은
     * 카드에 붙고 일정은 카드 없이도 만들 수 있다. 그래서 "그날 날짜로 남긴 기록이 있는가"로
     * 판단한다. 진료를 다녀와 <b>다음 날</b> 적으면서 날짜를 그날로 두면 이 일정은 계속
     * "기록 없음"으로 남는다 — 실제로 안 적은 것과 구별하지 못한다.
     *
     * <p><b>취소한 일정은 세지 않는다.</b> 안 간 진료의 기록을 재촉할 이유가 없다.
     * {@code DONE} 은 거르지 않는다 — 서버가 그 값을 세우는 곳이 없어서 믿을 수 없다.
     *
     * @param since 이 날짜 이후만 본다. 오래된 것까지 올리면 <b>때를 놓친 알림</b>이 된다
     */
    @Query("""
            select max(a.scheduledOn) from Appointment a
            where a.user.id = :userId
              and a.status <> :canceled
              and a.scheduledOn < :today
              and a.scheduledOn >= :since
              and not exists (
                  select 1 from VisitRecord v
                  where v.user.id = :userId
                    and v.visitedOn = a.scheduledOn)
            """)
    LocalDate findPendingRecordOn(@Param("userId") Long userId,
                                  @Param("canceled") Appointment.Status canceled,
                                  @Param("today") LocalDate today,
                                  @Param("since") LocalDate since);

    /**
     * 이 카드들을 가져가기로 한 일정을 찾는다. 카드를 지울 때 연결을 끊는 데 쓴다.
     *
     * <p>일정 자체는 남는다 — 카드 없이도 성립한다.
     */
    @Query("select distinct a from Appointment a join a.cards c where c.id in :cardIds")
    List<Appointment> findAllByCardIdIn(@Param("cardIds") Collection<Long> cardIds);

    void deleteAllByUser(User user);
}
