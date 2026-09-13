package com.jinryomate.backend.card.dto;

import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.card.dto.CardDtos.CardResponse;
import com.jinryomate.backend.visit.entity.VisitRecord;

/**
 * 카드에 매달린 것들. 카드 본문이 아니라 <b>카드 밖에서 카드를 가리키는</b> 값이다.
 *
 * <p><b>카드에는 병원이 없다.</b> 카드는 증상을 정리한 한 장이고, 병원은 둘 중 하나에서 온다.
 *
 * <ul>
 *   <li><b>일정</b> — 진료를 <b>받을</b> 병원. 시안 {@code 1e-1} 의 "진료받을 병원"
 *   <li><b>진료 기록</b> — 진료를 <b>받은</b> 병원. 기록 목록({@code 1j})이 쓰는 값
 *   </ul>
 *
 * <p>둘이 다를 수 있다. 예약은 A 병원에 잡아 두고 실제로는 B 병원에 갈 수 있고, 그때
 * 하나로 뭉쳐 놓으면 어느 쪽인지 화면에서 알 수 없다. 그래서 이름을 나눈다.
 *
 * <p>둘 다 없을 수 있다 — 일정을 안 잡았고 아직 진료도 안 봤으면 {@link #EMPTY} 다.
 */
public record CardLinks(CardResponse.Appointment appointment, CardResponse.Visit visit) {

    public static final CardLinks EMPTY = new CardLinks(null, null);

    public static CardLinks of(Appointment appointment, VisitRecord visit) {
        return new CardLinks(
                appointment == null ? null : new CardResponse.Appointment(
                        appointment.getId(),
                        appointment.getClinicName(),
                        appointment.getDepartment(),
                        appointment.getScheduledAt()),
                visit == null ? null : new CardResponse.Visit(
                        visit.getId(),
                        visit.getClinicName(),
                        visit.getVisitedOn()));
    }
}
