package com.jinryomate.backend.profile.entity;

public enum Sex {

    FEMALE,
    MALE,

    /** 카카오 동의를 거부했고 온보딩에서도 답하지 않은 경우. */
    UNSPECIFIED;

    /** 카카오는 "female" / "male" 소문자로 준다. */
    public static Sex fromKakao(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "female" -> FEMALE;
            case "male" -> MALE;
            default -> UNSPECIFIED;
        };
    }
}
