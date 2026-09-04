package com.jinryomate.backend.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 카카오 연동 설정.
 *
 * <p>{@code appId} 가 없으면 <b>기동 자체가 실패한다.</b> 예전에는 값이 없으면 대조를
 * 건너뛰었는데, 그러면 방어선이 꺼진 채로 로그인이 멀쩡히 성공해서 아무도 눈치채지 못한다.
 * 배포할 때 환경변수 하나를 빠뜨리는 것만으로 남의 앱 토큰이 통과하게 된다.
 *
 * <p>그래서 "값이 없으면 통과"가 아니라 "값이 없으면 안 뜬다"로 바꿨다. 로컬·테스트·CI 에는
 * 더미 값을 넣어둔다 — 빠뜨리면 빌드가 즉시 실패하므로 잊어버릴 수가 없다.
 *
 * @param apiBaseUrl 카카오 API 주소. 테스트에서 목 서버로 바꿔 끼운다
 * @param appId      우리 앱의 ID. 토큰 검증 시 이 값과 일치하는지 확인한다 —
 *                   다른 서비스에서 발급된 정상 카카오 토큰이 통과하는 것을 막는 유일한 방어선
 * @param adminKey   연결 끊기(unlink)에 쓴다. 없으면 unlink를 건너뛴다
 */
@Validated
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String apiBaseUrl,

        @NotNull(message = "KAKAO_APP_ID 가 필요합니다. 이 값이 없으면 다른 앱의 카카오 토큰을 걸러낼 수 없습니다.")
        Long appId,

        String adminKey
) {
    public boolean canUnlink() {
        return adminKey != null && !adminKey.isBlank();
    }
}
