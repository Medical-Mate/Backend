/**
 * 카카오 로그인과 인증.
 * 
 * <p>카카오에서는 <b>회원번호만</b> 받는다. 이름·나이·성별은 온보딩에서 직접 입력받는다
 * ({@link com.jinryomate.backend.profile}).
 * 
 * <p>앱이 보낸 카카오 토큰은 그대로 믿지 않는다. 서버가 카카오에 재검증하고,
 * 토큰의 앱 ID가 우리 앱인지까지 확인한 뒤 자체 JWT를 발급한다.
 * 
 * <p>device는 로그인 수단이 아니라 <b>푸시 대상</b>이다. S6 알림을 보내려면
 * 기기별 푸시 토큰이 필요하다. user 1 : device N.
 */
package com.jinryomate.backend.auth;
