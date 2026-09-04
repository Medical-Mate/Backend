package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.config.KakaoProperties;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 앱 ID 대조를 <b>실제 코드로</b> 검증한다.
 *
 * <p>{@code AuthApiTest} 는 {@code KakaoClient} 를 목으로 바꿔 "거부해라"라고 시켜놓고
 * 거부하는지 본다. 그래서 대조 로직이 통째로 빠져도 그 테스트는 통과한다.
 * 여기서는 카카오 <b>HTTP 응답</b>만 가짜로 만들고 검사 코드는 진짜를 태운다.
 */
class KakaoClientTest {

    private static final long OUR_APP_ID = 111111L;
    private static final String BASE_URL = "https://kapi.kakao.com";
    private static final String TOKEN_INFO_URL = BASE_URL + "/v1/user/access_token_info";

    @Test
    @DisplayName("우리 앱에서 발급된 토큰이면 회원번호를 돌려준다")
    void 우리_앱_토큰() {
        Fixture f = fixture(OUR_APP_ID);
        f.server.expect(requestTo(TOKEN_INFO_URL))
                .andRespond(withSuccess(tokenInfo(3847562910L, OUR_APP_ID), MediaType.APPLICATION_JSON));

        assertThat(f.client.resolveKakaoId("kakao-token")).isEqualTo(3847562910L);
        f.server.verify();
    }

    @Test
    @DisplayName("다른 앱에서 발급된 정상 토큰은 거부한다")
    void 남의_앱_토큰() {
        Fixture f = fixture(OUR_APP_ID);
        // 카카오 입장에서는 완전히 정상인 토큰이다. app_id 만 다르다.
        f.server.expect(requestTo(TOKEN_INFO_URL))
                .andRespond(withSuccess(tokenInfo(3847562910L, 999999L), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client.resolveKakaoId("남의-앱-토큰"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("카카오가 401을 주면 로그인에 실패한다")
    void 유효하지_않은_토큰() {
        Fixture f = fixture(OUR_APP_ID);
        f.server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> f.client.resolveKakaoId("아무-문자열"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("회원번호가 없는 응답은 거부한다")
    void 회원번호_없음() {
        Fixture f = fixture(OUR_APP_ID);
        f.server.expect(requestTo(TOKEN_INFO_URL))
                .andRespond(withSuccess("{\"app_id\":111111}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client.resolveKakaoId("kakao-token"))
                .isInstanceOf(ApiException.class);
    }

    // ---------- helpers ----------

    private record Fixture(KakaoClient client, MockRestServiceServer server) {}

    private Fixture fixture(Long appId) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoClient client = new KakaoClient(builder, new KakaoProperties(BASE_URL, appId, null));
        return new Fixture(client, server);
    }

    private String tokenInfo(long id, long appId) {
        return "{\"id\":%d,\"app_id\":%d,\"expires_in\":3600}".formatted(id, appId);
    }
}
