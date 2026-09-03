package com.jinryomate.backend.global.web;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상태 확인", description = "서버가 살아 있는지 확인")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Operation(summary = "헬스 체크", description = "무인증으로 열려 있습니다.")
    @SecurityRequirements
    @GetMapping("/health")
    public Health health() {
        return new Health(applicationName, "UP", OffsetDateTime.now());
    }

    public record Health(String application, String status, OffsetDateTime time) {}
}
