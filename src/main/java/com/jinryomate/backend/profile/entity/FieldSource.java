package com.jinryomate.backend.profile.entity;

/**
 * 값이 어디서 왔는지.
 *
 * <p>사용자가 온보딩에서 직접 고친 값을 다음 로그인 때 카카오 값으로 덮어쓰지 않기 위해 남긴다.
 * 이 구분이 없으면 이름을 고쳐도 재로그인마다 카카오 값으로 되돌아간다.
 */
public enum FieldSource {

    /** 카카오 동의항목에서 받아온 값. 덮어써도 된다. */
    KAKAO,

    /** 사용자가 직접 입력하거나 고친 값. 덮어쓰지 않는다. */
    SELF_INPUT
}
