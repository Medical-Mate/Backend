package com.jinryomate.backend.auth.web;

import com.jinryomate.backend.auth.dto.AuthDtos.DeviceRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.RefreshRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 앱이 카카오 SDK로 받은 액세스 토큰을 넘기면, 서버가 검증하고 자체 토큰을 준다. */
    @PostMapping("/auth/kakao")
    public TokenResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.kakaoAccessToken());
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    /** 푸시 알림 대상 등록. 같은 기기가 다시 오면 갱신한다. */
    @PostMapping("/me/devices")
    public ResponseEntity<Void> registerDevice(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody DeviceRequest request) {
        authService.registerDevice(userId, request.pushToken(), request.platform());
        return ResponseEntity.noContent().build();
    }

    /** 탈퇴. 이 사용자의 데이터를 지우고 카카오 연결을 끊는다. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        authService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
