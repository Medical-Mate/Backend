package com.jinryomate.backend.auth.dto;

import com.jinryomate.backend.auth.entity.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 인증 API의 요청·응답 모음. */
public final class AuthDtos {

    private AuthDtos() {}

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
     * <p><b>토글 셋 중 하나만 여기 있다.</b> "브리핑 카드 자동 저장"과 "진료실 화면 밝기
     * 최대"는 서버가 읽지도 않는 기기 취향이라 앱의 {@code DataStore} 에 둡니다.
     * 서버가 안 쓰는 값을 민감정보 서버에 보관할 이유가 없습니다.
     *
     * @param visitReminderEnabled 진료 하루 전 알림. 지금 예약은 앱이 하지만, 받을지 말지는
     *                             기기 취향이 아니라 그 사람의 선택이라 계정에 붙습니다
     */
    public record SettingsRequest(
            @NotNull(message = "알림 설정이 필요합니다.")
            Boolean visitReminderEnabled
    ) {}

    public record SettingsResponse(boolean visitReminderEnabled) {}

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
