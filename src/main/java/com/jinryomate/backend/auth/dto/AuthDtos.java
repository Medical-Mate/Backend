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
