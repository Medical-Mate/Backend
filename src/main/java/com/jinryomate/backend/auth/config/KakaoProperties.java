package com.jinryomate.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 연동 설정.
 *
 * @param apiBaseUrl 카카오 API 주소. 테스트에서 목 서버로 바꿔 끼운다
 * @param appId      우리 앱의 ID. 토큰 검증 시 이 값과 일치하는지 확인한다 —
 *                   다른 서비스에서 발급된 정상 카카오 토큰이 통과하는 것을 막는 유일한 방어선
 * @param adminKey   연결 끊기(unlink)에 쓴다. 없으면 unlink를 건너뛴다
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String apiBaseUrl,
        Long appId,
        String adminKey
) {
    public boolean canUnlink() {
        return adminKey != null && !adminKey.isBlank();
    }
}
