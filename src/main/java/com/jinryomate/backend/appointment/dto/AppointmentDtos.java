package com.jinryomate.backend.appointment.dto;

import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.appointment.entity.Appointment.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 진료 예정 일정의 요청·응답. */
public final class AppointmentDtos {

    private AppointmentDtos() {}

    // ---------- 요청 ----------

    public record CreateAppointmentRequest(
            @NotBlank(message = "병원명이 필요합니다.")
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            @Size(max = 40, message = "진료과는 40자 이내입니다.")
            String department,

            @Size(max = 60, message = "60자 이내로 입력해주세요.")
            String purpose,

            @NotNull(message = "예정 일시가 필요합니다.")
            Instant scheduledAt,

            /** 연결할 브리핑 카드. 없어도 된다 — 캘린더에서 바로 만드는 경로가 있다. */
            Long cardId
    ) {}

    /**
     * 수정. 보낸 필드만 바뀐다.
     *
     * @param clearCard 카드 연결을 끊을 때 {@code true}. {@code cardId} 의 null 을
     *                  "안 바꿈"과 "지움" 두 뜻으로 쓰면 연결을 끊을 방법이 없다
     */
    public record UpdateAppointmentRequest(
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            @Size(max = 40, message = "진료과는 40자 이내입니다.")
            String department,

            @Size(max = 60, message = "60자 이내로 입력해주세요.")
            String purpose,

            Instant scheduledAt,
            Status status,
            Long cardId,
            boolean clearCard
    ) {}

    // ---------- 응답 ----------

    /**
     * @param scheduledAt 날짜·시각만 준다. <b>D-day 는 앱이 센다</b> — 서버 시간대와
     *                    사용자 시간대가 어긋나면 하루 틀린다
     * @param cardTitle   연결된 카드 제목. 화면의 "복부 통증 브리핑 카드"
     */
    public record AppointmentResponse(
            Long appointmentId,
            String clinicName,
            String department,
            String purpose,
            Instant scheduledAt,
            Status status,
            Long cardId,
            String cardTitle
    ) {
        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(),
                    a.getClinicName(),
                    a.getDepartment(),
                    a.getPurpose(),
                    a.getScheduledAt(),
                    a.getStatus(),
                    a.getCard() == null ? null : a.getCard().getId(),
                    a.getCard() == null ? null : a.getCard().displayTitle());
        }
    }
}
