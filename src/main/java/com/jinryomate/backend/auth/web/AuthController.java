package com.jinryomate.backend.auth.web;

import com.jinryomate.backend.auth.dto.AuthDtos.DeviceRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.RefreshRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.SettingsRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.SettingsResponse;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "카카오 로그인, 토큰 재발급, 기기 등록, 탈퇴")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "카카오 로그인",
            description = """
                    앱이 카카오 SDK로 로그인해 받은 **액세스 토큰**을 보냅니다.
                    인가 코드(authorization code)가 아닙니다 — 토큰 교환은 앱이 SDK로 끝낸 뒤입니다.

                    서버는 그 토큰을 카카오에 다시 물어 회원번호를 확인하고, 토큰이 우리 앱에서
                    발급된 것인지까지 검증한 뒤 자체 JWT를 발급합니다.

                    동의항목으로 받은 이름·성별·출생연도·생일이 있으면 프로필에 미리 채워둡니다.
                    동의를 거부해 값이 없어도 로그인은 정상 진행되고, 온보딩에서 직접 받으면 됩니다.

                    처음 온 회원번호면 가입시키고, 있으면 그 사용자로 로그인합니다.
                    """)
    @SecurityRequirements
    @PostMapping("/auth/kakao")
    public TokenResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.kakaoAccessToken());
    }

    @Operation(
            summary = "액세스 토큰 재발급",
            description = """
                    refresh 토큰으로 새 액세스 토큰을 받습니다.

                    **한 번 쓴 refresh 토큰은 즉시 폐기되고 새 토큰이 함께 내려옵니다.**
                    응답에 담긴 새 refresh 토큰으로 갈아끼우세요. 이전 토큰을 다시 보내면 401입니다.
                    """)
    @SecurityRequirements
    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(
            summary = "로그아웃",
            description = "이 사용자의 모든 refresh 토큰을 폐기합니다. 액세스 토큰은 만료까지 유효합니다.")
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "푸시 토큰 등록 (현재 미사용)",
            description = """
                    > **지금은 부를 필요가 없습니다.** 진료 하루 전 알림은 **앱이 로컬로 예약**하기로
                    > 정했습니다. 서버가 푸시를 보내지 않으므로 이 토큰을 쓰는 곳이 없습니다.

                    Render 무료 플랜은 15분 유휴 후 잠들어, 알림이 나가야 하는 시간에
                    서버가 자고 있습니다. 서버 안 스케줄러로는 알림이 아예 안 갑니다.
                    일정 조회(`GET /api/me/appointments`)가 예약할 재료를 다 주므로
                    앱이 기기에 예약하는 편이 낫다고 판단했습니다.

                    **자리는 남겨둡니다.** 여러 기기 동기화가 필요해지거나 서버 푸시로 옮길 때
                    이 엔드포인트를 그대로 씁니다.

                    앱이 FCM·APNs에서 받은 푸시 토큰을 **서버로 올립니다.** 서버가 주는 게 아닙니다.
                    푸시 토큰은 앱 재설치나 OS 사정으로 갱신되므로, 바뀔 때마다 다시 올려주세요.

                    같은 푸시 토큰이 다시 오면 새로 만들지 않고 소유자까지 갱신합니다.
                    한 기기를 여러 계정이 번갈아 쓰는 경우 이전 사용자의 알림이 가면 안 되기 때문입니다.
                    """)
    @PostMapping("/me/devices")
    public ResponseEntity<Void> registerDevice(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody DeviceRequest request) {
        authService.registerDevice(userId, request.pushToken(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "계정 설정 조회",
            description = """
                    화면 `1s-1` 의 토글. **셋 중 하나만 여기 있습니다.**

                    "브리핑 카드 자동 저장"과 "진료실 화면 밝기 최대"는 서버가 읽지도 않는
                    기기 취향이라 앱의 `DataStore` 에 두세요. 서버가 안 쓰는 값을
                    민감정보 서버에 보관할 이유가 없습니다.

                    `visitReminderEnabled`(진료 하루 전 알림)만 계정에 따라다닙니다.

                    **지금 알림을 예약하는 것은 앱입니다**(`POST /me/devices` 설명 참고).
                    그래도 이 값만 서버에 두는 이유는, 알림을 받을지 말지는 기기 취향이
                    아니라 **그 사람의 선택**이기 때문입니다. 기기를 바꾸거나 앱을 다시
                    깔면 "안 받겠다"고 한 사람에게 알림이 다시 가기 시작합니다.
                    로그인 직후 이 값을 읽어 예약 여부를 정하시면 됩니다.

                    서버 푸시로 옮길 때도 같은 값을 그대로 씁니다. 기본값은 켜짐입니다.
                    """)
    @GetMapping("/me/settings")
    public SettingsResponse getSettings(@AuthenticationPrincipal Long userId) {
        return authService.getSettings(userId);
    }

    @Operation(summary = "계정 설정 변경", description = "진료 하루 전 알림을 켜고 끕니다.")
    @PatchMapping("/me/settings")
    public SettingsResponse updateSettings(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody SettingsRequest request) {
        return authService.updateSettings(userId, request.visitReminderEnabled());
    }

    @Operation(
            summary = "탈퇴",
            description = """
                    카카오 연결을 끊고 이 사용자의 데이터를 지웁니다.
                    프로필·기기·토큰이 함께 삭제되며 되돌릴 수 없습니다.
                    """)
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        authService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
