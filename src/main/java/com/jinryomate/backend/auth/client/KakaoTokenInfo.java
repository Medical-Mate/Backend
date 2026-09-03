package com.jinryomate.backend.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 {@code /v1/user/access_token_info} 응답.
 *
 * @param id     카카오 회원번호. 우리가 계정을 식별하는 유일한 값
 * @param appId  이 토큰이 발급된 앱. 우리 앱이 맞는지 반드시 확인해야 한다
 */
public record KakaoTokenInfo(
        Long id,
        @JsonProperty("app_id") Long appId,
        @JsonProperty("expires_in") Long expiresIn
) {}
