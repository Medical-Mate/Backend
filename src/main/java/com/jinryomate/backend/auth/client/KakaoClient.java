package com.jinryomate.backend.auth.client;

import com.jinryomate.backend.auth.config.KakaoProperties;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 호출.
 *
 * <p>앱이 보낸 액세스 토큰을 서버가 다시 검증하는 것이 이 클래스의 존재 이유다.
 * 검증 없이 받으면 아무 문자열이나 보내 남의 계정으로 들어올 수 있다.
 */
@Slf4j
@Component
public class KakaoClient {

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoClient(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
    }

    /**
     * 액세스 토큰의 주인을 확인한다.
     *
     * @return 카카오 회원번호
     * @throws ApiException 토큰이 유효하지 않거나 <b>다른 앱에서 발급된</b> 경우
     */
    public Long resolveKakaoId(String kakaoAccessToken) {
        KakaoTokenInfo info;
        try {
            info = restClient.get()
                    .uri("/v1/user/access_token_info")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
        } catch (Exception e) {
            // 카카오가 준 에러 본문에 토큰이 들어 있을 수 있어 메시지를 그대로 남기지 않는다.
            log.warn("카카오 토큰 검증 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다.");
        }

        if (info == null || info.id() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다.");
        }

        // 다른 서비스에서 발급된 정상 카카오 토큰이 그대로 통과하는 것을 막는 유일한 방어선.
        if (properties.appId() != null && !properties.appId().equals(info.appId())) {
            log.warn("다른 앱의 카카오 토큰 유입 — expected={}, actual={}",
                    properties.appId(), info.appId());
            throw new ApiException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다.");
        }

        return info.id();
    }

    /**
     * 연결 끊기. 어드민 키가 설정돼 있을 때만 시도한다.
     *
     * <p>실패해도 예외를 던지지 않는다. 탈퇴 요청은 카카오 API 사정과 무관하게 처리돼야 한다.
     */
    public void unlink(Long kakaoId) {
        if (!properties.canUnlink()) {
            log.info("카카오 어드민 키가 없어 unlink를 건너뜁니다. kakaoId={}", kakaoId);
            return;
        }
        try {
            restClient.post()
                    .uri("/v1/user/unlink")
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("target_id_type=user_id&target_id=" + kakaoId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("카카오 unlink 실패 — 로컬 데이터는 삭제합니다. kakaoId={}, {}",
                    kakaoId, e.getClass().getSimpleName());
        }
    }
}
