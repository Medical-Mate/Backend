package com.jinryomate.backend.auth.web;

import com.jinryomate.backend.auth.config.KakaoProperties;
import com.jinryomate.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 연결 해제 웹훅.
 *
 * <p>사용자가 <b>카카오 설정에서</b> 우리 앱 연결을 끊었을 때 카카오가 부른다.
 * 이게 없으면 그 사람의 증상·복용약이 우리 DB 에 그대로 남는다 — 민감정보를 다루는
 * 서비스라 "더 이상 쓰지 않겠다"는 의사 표시를 못 받는 것이 문제다.
 *
 * <p><b>인증 없이 열리는 경로다.</b> 그래서 어드민 키 대조가 유일한 방어선이다.
 * 검증이 없으면 누구나 임의의 회원번호로 남을 탈퇴시킬 수 있다.
 *
 * <p>카카오는 <b>3초 이내 200</b> 을 기대하고, 연결 해제 웹훅은 <b>재전송하지 않는다</b>.
 * Render 무료 플랜이 잠들어 있으면 첫 요청이 수십 초 걸려 유실될 수 있다 —
 * 알려진 한계이고, 없는 것보다는 낫다는 판단이다.
 */
@Slf4j
@Tag(name = "카카오 웹훅", description = "카카오가 부르는 경로. 앱이 부르지 않습니다")
@RestController
@RequiredArgsConstructor
public class KakaoUnlinkWebhookController {

    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";

    private final KakaoProperties kakaoProperties;
    private final AuthService authService;

    @Operation(
            summary = "연결 해제 웹훅 (카카오 전용)",
            description = """
                    **앱이 부르는 경로가 아닙니다.** 사용자가 카카오 설정에서 우리 앱 연결을
                    끊었을 때 카카오가 부릅니다.

                    `Authorization: KakaoAK {대표 어드민 키}` 로 검증합니다. 키가 다르거나
                    `app_id` 가 우리 앱이 아니면 **아무것도 하지 않습니다.**

                    카카오 규격상 **성공·실패와 무관하게 200** 을 돌려줍니다. 오류를 내면
                    카카오가 실패로 보고 웹훅을 비활성화할 수 있습니다.

                    카카오 콘솔의 **앱 설정 → 웹훅** 에 이 주소를 등록해야 동작합니다.
                    """)
    @PostMapping(value = "/webhooks/kakao/unlink",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> unlinked(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "app_id", required = false) Long appId,
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "referrer_type", required = false) String referrerType) {

        if (!isFromKakao(authorization, appId)) {
            // 어느 값이 틀렸는지는 남기지 않는다. 무작위 대입의 힌트가 된다.
            log.warn("카카오 웹훅 검증 실패 — 무시한다");
            return ResponseEntity.ok().build();
        }
        if (userId == null) {
            log.warn("카카오 웹훅에 user_id 가 없다 — 무시한다");
            return ResponseEntity.ok().build();
        }

        // 회원번호는 개인정보라 로그에 남기지 않는다. 해제 경로만 남긴다.
        log.info("카카오 연결 해제 웹훅 수신 referrerType={}", referrerType);
        authService.withdrawByKakaoId(userId);

        return ResponseEntity.ok().build();
    }

    /**
     * 카카오가 보낸 것이 맞는지.
     *
     * <p>어드민 키가 맞아도 {@code app_id} 가 다르면 우리 사용자가 아니다.
     * 한 어드민 키가 여러 앱을 관리할 수 있으므로 둘 다 본다.
     */
    private boolean isFromKakao(String authorization, Long appId) {
        if (authorization == null || !authorization.startsWith(ADMIN_KEY_PREFIX)) {
            return false;
        }
        String key = authorization.substring(ADMIN_KEY_PREFIX.length()).trim();

        // 어드민 키가 설정돼 있지 않으면 검증할 수단이 없다. 그때는 받지 않는다.
        return kakaoProperties.canUnlink()
                && kakaoProperties.adminKey().equals(key)
                && kakaoProperties.appId().equals(appId);
    }
}
