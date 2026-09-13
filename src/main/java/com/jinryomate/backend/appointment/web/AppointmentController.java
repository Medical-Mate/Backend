package com.jinryomate.backend.appointment.web;

import com.jinryomate.backend.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.UpdateAppointmentRequest;
import com.jinryomate.backend.appointment.service.AppointmentService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "진료 예정 일정", description = "캘린더(화면 1r)와 홈의 다가오는 일정(화면 1n)")
@RestController
@RequestMapping("/api/me/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(
            summary = "일정 조회 (월별 또는 일자별)",
            description = """
                    **`date` 를 주면 그 하루**, **`year`·`month` 를 주면 그 달**을 돌려줍니다.

                    - 월 뷰의 점 표시 → `year`·`month`
                    - 일자를 눌렀을 때 → `date`

                    **날짜(`scheduledOn`)와 시각(`scheduledTime`)이 따로 옵니다.**
                    날짜로 견주므로 시간대 때문에 하루가 밀리는 일이 없습니다.
                    `scheduledTime` 이 `null` 이면 **시간 미정**입니다.

                    **D-day 는 앱이 세세요** — 서버가 계산하면 사용자 시간대와
                    어긋날 때 하루 틀립니다.
                    """)
    @GetMapping
    public List<AppointmentResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date != null) {
            return appointmentService.listByDate(userId, date);
        }
        if (year != null && month != null) {
            return appointmentService.listByMonth(userId, year, month);
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST,
                "date 또는 year·month 를 함께 보내주세요.");
    }

    @Operation(
            summary = "다가오는 일정",
            description = """
                    홈 화면의 "다가오는 일정"입니다. **아직 안 지났고 취소되지 않은** 것만,
                    가까운 순으로 옵니다.

                    홈은 보통 하나만 쓰지만 목록으로 돌려줍니다 — 같은 날 둘이 잡힐 수 있고,
                    몇 개를 보여줄지는 화면이 정합니다.
                    """)
    @GetMapping("/upcoming")
    public List<AppointmentResponse> upcoming(@AuthenticationPrincipal Long userId) {
        return appointmentService.listUpcoming(userId);
    }

    @Operation(
            summary = "일정 등록",
            description = """
                    캘린더의 `+` 버튼입니다.

                    **날짜(`scheduledOn`)만 필수입니다.** 시각(`scheduledTime`)은 빼고
                    만들 수 있고, 그 일정은 "시간 미정"으로 옵니다 — 화면 `1r-2-A` 의
                    "시간 정하고 확정하기" 가 그 상태입니다.

                    **카드는 여러 장 붙일 수 있습니다.** `cardIds` 에 배열로 보내세요 —
                    화면 `1r-4-B` 가 체크박스이고 개수를 찍습니다. 카드 없이
                    "다음 주 치과"만 적어도 됩니다. 남의 카드를 붙이려 하면 404입니다.

                    **`origin`** 은 이 일정이 어디서 왔는지입니다. 진료 후 기록의
                    재방문에서 만들었으면 `VISIT_FOLLOW_UP`, 안 보내면 `MANUAL` 입니다.

                    병원명과 진료과는 따로 받습니다. 병원 검색이 붙으면 그대로 채워집니다.
                    화면의 "서울OO병원 내과 재진"은 앱이 세 필드를 조합해 만드세요.
                    """)
    @PostMapping
    public AppointmentResponse create(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.create(userId, request);
    }

    @Operation(
            summary = "일정 수정",
            description = """
                    보낸 필드만 바뀝니다.

                    **`cardIds` 는 통째로 갈아끼웁니다.** 체크를 하나 풀었을 때도 화면에
                    남아 있는 목록을 전부 보내세요. `null` 은 "안 바꿈", `[]` 는
                    "전부 뗌" 입니다.

                    **시각을 다시 "미정"으로 되돌리려면 `clearTime: true`** 를 보내세요.
                    `scheduledTime: null` 은 "안 바꿈"으로 봅니다 — null 을 두 뜻으로
                    쓰면 시각을 지울 방법이 없습니다.

                    **날짜(`scheduledOn`)는 지울 수 없습니다.** 날짜 없는 일정은
                    캘린더에 그릴 자리가 없어서, 없앨 거면 일정을 지우는 게 맞습니다.
                    """)
    @PatchMapping("/{appointmentId}")
    public AppointmentResponse update(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long appointmentId,
                                      @Valid @RequestBody UpdateAppointmentRequest request) {
        return appointmentService.update(userId, appointmentId, request);
    }

    @Operation(summary = "일정 삭제")
    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long appointmentId) {
        appointmentService.delete(userId, appointmentId);
        return ResponseEntity.noContent().build();
    }
}
