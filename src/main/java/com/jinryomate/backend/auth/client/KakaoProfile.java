package com.jinryomate.backend.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 {@code /v2/user/me} 응답 중 우리가 쓰는 부분.
 *
 * <p><b>모든 필드가 없을 수 있다.</b> 선택 동의는 사용자가 거부할 수 있고, 검수 전에는
 * 항목 자체가 내려오지 않는다. 값이 없는 경우가 예외가 아니라 정상 경로다.
 *
 * <p>연령대({@code age_range})는 받지 않는다. {@code "30~39"} 구간이라 카드의 {@code 32세}를
 * 만들 수 없어서, 출생연도만 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoProfile(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            String name,
            String gender,
            String birthyear
    ) {}

    public String name() {
        return kakaoAccount == null ? null : kakaoAccount.name();
    }

    public String gender() {
        return kakaoAccount == null ? null : kakaoAccount.gender();
    }

    /** 카카오는 출생연도를 문자열로 준다. 숫자가 아니면 없는 것으로 본다. */
    public Integer birthYear() {
        if (kakaoAccount == null || kakaoAccount.birthyear() == null) {
            return null;
        }
        try {
            return Integer.valueOf(kakaoAccount.birthyear());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
