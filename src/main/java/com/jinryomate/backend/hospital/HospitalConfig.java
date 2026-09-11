package com.jinryomate.backend.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.hospital.client.HospitalSearchClient;
import com.jinryomate.backend.hospital.client.HttpHospitalSearchClient;
import com.jinryomate.backend.hospital.client.StubHospitalSearchClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 병원 검색 클라이언트를 고른다.
 *
 * <p><b>서비스 키가 있으면 심평원, 없으면 스텁이다.</b> AI 클라이언트와 같은 방식이고 이유도 같다 —
 * 로컬 개발과 테스트에서는 키가 없는 것이 정상이라 기동을 막으면 개발자마다 키를 받아야 한다.
 * 어느 쪽이 물렸는지는 기동 로그에 남는다.
 */
@Slf4j
@Configuration
public class HospitalConfig {

    @Bean
    public RestClient hospitalRestClient(RestClient.Builder builder, HospitalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(properties.timeout());
        return builder.requestFactory(factory).build();
    }

    @Bean
    @ConditionalOnExpression("!'${hospital.service-key:}'.isBlank()")
    public HospitalSearchClient httpHospitalSearchClient(RestClient hospitalRestClient,
                                                         ObjectMapper objectMapper,
                                                         HospitalProperties properties) {
        log.info("병원 검색: 심평원에 붙습니다 baseUrl={}", properties.baseUrl());
        return new HttpHospitalSearchClient(hospitalRestClient, objectMapper, properties);
    }

    @Bean
    @ConditionalOnExpression("'${hospital.service-key:}'.isBlank()")
    public HospitalSearchClient stubHospitalSearchClient() {
        log.warn("병원 검색: 스텁으로 돕니다. HIRA_SERVICE_KEY 가 비어 있습니다.");
        return new StubHospitalSearchClient();
    }
}
