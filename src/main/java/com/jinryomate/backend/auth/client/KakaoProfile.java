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
 * 만들 수 없어서, 출생연도와 생일만 쓴다.
 *
 * <p><b>생일과 출생연도는 별개 동의항목이다.</b> 생일만 켜면 {@code MMDD}만 오고 연도가 없어
 * 나이를 만들 수 없다. 콘솔에서 둘 다 켜야 한다.
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
            String birthyear,
            /** {@code "MMDD"} 형식. 연도는 들어 있지 않다. */
            String birthday,
            /** {@code "SOLAR"} 또는 {@code "LUNAR"}. */
            @JsonProperty("birthday_type") String birthdayType
    ) {}
    // 카카오는 is_leap_month(윤달 여부)도 함께 주지만 읽지 않는다.
    // 음력 생일을 나이 계산에서 통째로 제외하므로 윤달만 따로 볼 이유가 없다.
    // 나중에 음력→양력 변환을 넣게 되면 그때 이 필드가 필요하다.

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

    /**
     * 생일을 {@code "MM-dd"} 형식으로. 만 나이를 정확히 계산하는 데 쓴다.
     *
     * <p><b>음력 생일은 쓰지 않는다.</b> 양력으로 변환하지 않고 그대로 계산하면 오히려 틀린
     * 나이가 나온다. 이 경우 출생연도만으로 근사한다.
     */
    public String birthMonthDay() {
        if (kakaoAccount == null || kakaoAccount.birthday() == null) {
            return null;
        }
        if ("LUNAR".equalsIgnoreCase(kakaoAccount.birthdayType())) {
            return null;
        }
        String raw = kakaoAccount.birthday();
        if (raw.length() != 4 || !raw.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return raw.substring(0, 2) + "-" + raw.substring(2);
    }
}
