package com.jinryomate.backend.appointment.dto;

import com.jinryomate.backend.appointment.entity.Appointment;
import com.jinryomate.backend.appointment.entity.Appointment.Status;
import com.jinryomate.backend.appointment.entity.AppointmentTodo;
import com.jinryomate.backend.appointment.entity.Origin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 진료 예정 일정의 요청·응답. */
public final class AppointmentDtos {

    private AppointmentDtos() {}

    // ---------- 요청 ----------

    /**
     * @param scheduledOn   <b>날짜만 필수입니다.</b> 시각은 나중에 정할 수 있습니다
     * @param scheduledTime {@code "10:30"}. 안 보내면 "시간 미정" 입니다
     * @param cardIds       이 진료에 가져갈 브리핑 카드. 여러 장 가능합니다
     * @param origin        진료 후 기록의 재방문에서 만든 것이면 {@code VISIT_FOLLOW_UP}.
     *                      비우면 {@code MANUAL} 입니다
     */
    public record CreateAppointmentRequest(
            @NotBlank(message = "병원명이 필요합니다.")
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            @Size(max = 40, message = "진료과는 40자 이내입니다.")
            String department,

            @Size(max = 60, message = "60자 이내로 입력해주세요.")
            String purpose,

            @NotNull(message = "예정 날짜가 필요합니다.")
            LocalDate scheduledOn,

            LocalTime scheduledTime,

            @Size(max = 10, message = "카드는 10장까지입니다.")
            List<Long> cardIds,

            Origin origin,

            /** 진료 전 할 일. 화면 1r-4. */
            @Valid
            @Size(max = 20, message = "할 일은 20개까지입니다.")
            List<TodoRequest> todos
    ) {}

    /**
     * 할 일 한 줄.
     *
     * <p><b>목록째 보냅니다.</b> 체크 하나를 켤 때도 화면에 있는 목록을 전부 보내세요 —
     * 보낸 목록이 그대로 저장됩니다. 빼고 보내면 지워집니다.
     */
    public record TodoRequest(
            @NotBlank(message = "할 일 내용이 필요합니다.")
            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String text,

            boolean done
    ) {}

    /**
     * 수정. 보낸 필드만 바뀝니다.
     *
     * @param cardIds   보내면 <b>통째로 갈아끼웁니다.</b> {@code null} 은 "안 바꿈",
     *                  {@code []} 는 "전부 뗌" 입니다
     * @param clearTime 시각을 다시 "미정"으로 되돌릴 때 {@code true}. {@code scheduledTime}
     *                  의 null 을 "안 바꿈"과 "지움" 두 뜻으로 쓸 수 없어 따로 받습니다
     */
    public record UpdateAppointmentRequest(
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            @Size(max = 40, message = "진료과는 40자 이내입니다.")
            String department,

            @Size(max = 60, message = "60자 이내로 입력해주세요.")
            String purpose,

            LocalDate scheduledOn,
            LocalTime scheduledTime,
            boolean clearTime,

            Status status,

            @Size(max = 10, message = "카드는 10장까지입니다.")
            List<Long> cardIds,

            @Valid
            @Size(max = 20, message = "할 일은 20개까지입니다.")
            List<TodoRequest> todos
    ) {}

    // ---------- 응답 ----------

    /**
     * @param scheduledOn   날짜. <b>D-day 는 앱이 셉니다</b> — 서버가 계산하면 사용자
     *                      시간대와 어긋날 때 하루 틀립니다
     * @param scheduledTime 안 정했으면 {@code null} 입니다. 화면 {@code 1r-2-A} 가
     *                      그때 "시간 정하고 확정하기" 를 띄웁니다
     * @param origin        {@code VISIT_FOLLOW_UP} 이면 "진료 후 기록에서 자동으로
     *                      만들었어요" 를 찍습니다
     * @param cards         가져갈 브리핑 카드. 화면의 "복부 통증 브리핑 카드를 가져가요"
     */
    public record AppointmentResponse(
            Long appointmentId,
            String clinicName,
            String department,
            String purpose,
            LocalDate scheduledOn,
            LocalTime scheduledTime,
            Status status,
            Origin origin,
            List<LinkedCard> cards,
            List<AppointmentTodo> todos
    ) {
        /** 일정에 붙은 카드 한 장. 목록에 줄을 그리고 눌러 들어갈 만큼만 담는다. */
        public record LinkedCard(Long cardId, String title) {}

        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(),
                    a.getClinicName(),
                    a.getDepartment(),
                    a.getPurpose(),
                    a.getScheduledOn(),
                    a.getScheduledTime(),
                    a.getStatus(),
                    a.getOrigin(),
                    a.getCards().stream()
                            .map(c -> new LinkedCard(c.getId(), c.displayTitle()))
                            .toList(),
                    List.copyOf(a.getTodos()));
        }
    }
}
