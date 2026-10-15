package com.jinryomate.backend.auth.dto;

import com.jinryomate.backend.auth.entity.Device;
import com.jinryomate.backend.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 인증 API의 요청·응답 모음. */
public final class AuthDtos {

    private AuthDtos() {}

    /** 알림 시각. 00:00 ~ 23:59, 두 자리 시·분만. */
    private static final String HH_MM = "^([01][0-9]|2[0-3]):[0-5][0-9]$";

    private static final DateTimeFormatter HH_MM_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 앱이 카카오 SDK로 로그인해 받은 액세스 토큰.
     * 서버는 이 값을 그대로 믿지 않고 카카오에 다시 물어 확인한다.
     */
    public record KakaoLoginRequest(
            @NotBlank(message = "카카오 액세스 토큰이 필요합니다.")
            String kakaoAccessToken
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "refresh 토큰이 필요합니다.")
            String refreshToken
    ) {}

    public record DeviceRequest(
            @NotBlank(message = "푸시 토큰이 필요합니다.")
            String pushToken,

            @NotNull(message = "플랫폼이 필요합니다.")
            Device.Platform platform
    ) {}

    /**
     * 계정에 따라다녀야 하는 설정. 화면 {@code 1s-1}.
     *
     * <p><b>알림 설정만 여기 있다.</b> "브리핑 카드 자동 저장"·"진료실 화면 밝기 최대"·
     * "헤이 메이트로 부르기"는 이 기기에서 어떻게 보일지, 이 기기에 무엇을 받을지의
     * 문제라 앱의 {@code DataStore} 에 둡니다. 서버가 안 쓰는 값을 민감정보 서버에
     * 보관할 이유가 없습니다.
     *
     * <p><b>보낸 필드만 바뀝니다.</b> {@code null} 이거나 빠진 필드는 그대로 둡니다.
     * 시각 하나만 고칠 때 토글을 같이 보낼 필요가 없습니다. {@code {}} 를 보내면 아무것도
     * 안 바꾸고 지금 설정을 돌려줍니다.
     *
     * <p><b>시각은 {@code HH:mm} 만 받습니다.</b> {@code "07:00"} 은 되고 {@code "7:00"}·
     * {@code "24:00"}·{@code "19:00:00"} 은 400 입니다. 초를 받아 조용히 버리거나
     * {@code 24:00} 을 자정으로 바꿔 저장하면 화면에 보이는 값과 울리는 시각이 어긋납니다.
     *
     * @param visitReminderEnabled  진료 전 알림. 시각을 적은 일정은 24시간 전, 시간 미정
     *                              일정은 전날 {@code visitReminderTime} 에 울립니다
     * @param recordReminderEnabled 진료 후 기록 알림. 시각을 적은 일정은 2시간 뒤, 시간
     *                              미정 일정은 당일 {@code recordReminderTime} 에 울립니다
     * @param visitReminderTime     시간 미정 일정의 전날 알림 시각. 시간대 없이 한국 시각
     * @param recordReminderTime    시간 미정 일정의 당일 기록 알림 시각. 시간대 없이 한국 시각
     */
    public record SettingsRequest(
            Boolean visitReminderEnabled,

            Boolean recordReminderEnabled,

            @Schema(example = "19:00", pattern = HH_MM)
            @Pattern(regexp = HH_MM, message = "알림 시각은 HH:mm 형식입니다. 예: 07:00, 19:30")
            String visitReminderTime,

            @Schema(example = "19:00", pattern = HH_MM)
            @Pattern(regexp = HH_MM, message = "알림 시각은 HH:mm 형식입니다. 예: 07:00, 19:30")
            String recordReminderTime
    ) {
        /** 검증을 통과한 뒤에만 부른다. 비었으면 {@code null}(그대로 둠). */
        public static LocalTime parseTime(String hhmm) {
            return hhmm == null ? null : LocalTime.parse(hhmm, HH_MM_FORMAT);
        }
    }

    /**
     * 네 값을 전부 냅니다. 시각은 요청과 같은 {@code HH:mm} 입니다.
     *
     * @param visitReminderTime  시간 미정 일정의 전날 알림 시각. 기본 {@code "19:00"}
     * @param recordReminderTime 시간 미정 일정의 당일 기록 알림 시각. 기본 {@code "19:00"}
     */
    public record SettingsResponse(
            boolean visitReminderEnabled,
            boolean recordReminderEnabled,
            @Schema(example = "19:00") String visitReminderTime,
            @Schema(example = "19:00") String recordReminderTime
    ) {
        public static SettingsResponse from(User user) {
            return new SettingsResponse(
                    user.isVisitReminderEnabled(),
                    user.isRecordReminderEnabled(),
                    user.getVisitReminderTime().format(HH_MM_FORMAT),
                    user.getRecordReminderTime().format(HH_MM_FORMAT));
        }
    }

    /**
     * @param onboardingRequired 온보딩(이름·나이·성별 등)을 아직 안 마친 사용자면 true.
     *                           앱이 로그인 직후 어느 화면으로 보낼지 정하는 데 쓴다.
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long accessExpiresInSeconds,
            boolean onboardingRequired
    ) {}
}
