package com.jinryomate.backend.handoff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 링크 설정.
 *
 * @param shareTtl      링크 수명. 인증 없이 열리는 주소라 <b>만료가 유일한 방어선</b>이다.
 *                      앱이 S6 화면에 들어올 때마다 새로 발급하면 짧아도 불편하지 않다
 * @param publicBaseUrl 공유 주소를 만들 때 앞에 붙는 값. 배포 환경마다 다르다
 */
@ConfigurationProperties(prefix = "handoff")
public record HandoffProperties(
        Duration shareTtl,
        String publicBaseUrl
) {}
