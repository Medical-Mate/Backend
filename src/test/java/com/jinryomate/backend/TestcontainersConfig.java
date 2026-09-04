package com.jinryomate.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL.
 *
 * <p>H2 대신 실제 PostgreSQL 을 띄운다. 스키마를 Flyway 마이그레이션이 만들기 때문에
 * <b>마이그레이션이 깨지면 테스트가 함께 깨진다</b> — H2 로는 잡히지 않는 사고다.
 * 운영과 같은 DB, 같은 스키마 생성 경로로 검증한다.
 *
 * <p>컨테이너는 JVM 당 하나만 띄우고 모든 테스트가 공유한다. 테스트 클래스마다
 * 새로 띄우면 CI 가 몇 분씩 늘어난다. 종료는 Ryuk 이 맡는다.
 *
 * <p><b>Docker 가 필요하다.</b> Docker 없는 환경에서는 테스트가 돌지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("jinryomate")
                    .withUsername("jinryomate")
                    .withPassword("test")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}
