package com.jinryomate.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.AiTurnClient;
import com.jinryomate.backend.ai.client.HttpAiTurnClient;
import com.jinryomate.backend.ai.client.StubAiTurnClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 문답 AI 클라이언트를 고른다.
 *
 * <p><b>{@code MEDIMATE_HMAC_SECRET} 이 있으면 실제 호출, 없으면 스텁이다.</b> 시크릿이
 * 비었는데 실제 호출로 붙으면 AI 가 요청을 전부 거부하고, 그 실패가 문답 첫 화면에서
 * 터진다. 로컬 개발과 테스트에서는 시크릿이 없는 것이 정상이라 스텁으로 돈다.
 *
 * <p>기동을 막지 않는 이유 — {@code KAKAO_APP_ID} 는 없으면 기동을 막지만 그건 보안
 * 방어선이 조용히 꺼지는 경우다. 여기는 다르다. 시크릿이 없으면 스텁이 도는 것이
 * 화면에 바로 드러나므로 조용히 뚫리지 않는다.
 */
@Slf4j
@Configuration
public class AiClientConfig {

    /** AI 호출 전용 {@code RestClient}. 타임아웃을 따로 잡아야 해서 공용 빌더를 그대로 쓰지 않는다. */
    @Bean
    public RestClient aiRestClient(RestClient.Builder builder, AiProperties properties) {
        Duration turn = properties.timeout().turn();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(turn);

        return builder.baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    @ConditionalOnExpression("!'${ai.hmac-secret:}'.isBlank()")
    public AiTurnClient httpAiTurnClient(RestClient aiRestClient,
                                         ObjectMapper objectMapper,
                                         AiProperties properties) {
        log.info("AI 문답: 실제 서비스에 붙습니다 baseUrl={}", properties.baseUrl());
        return new HttpAiTurnClient(aiRestClient, objectMapper, new AiSigner(properties.hmacSecret()));
    }

    @Bean
    @ConditionalOnExpression("'${ai.hmac-secret:}'.isBlank()")
    public AiTurnClient stubAiTurnClient() {
        log.warn("AI 문답: 스텁으로 돕니다. MEDIMATE_HMAC_SECRET 이 비어 있습니다.");
        return new StubAiTurnClient();
    }
}
