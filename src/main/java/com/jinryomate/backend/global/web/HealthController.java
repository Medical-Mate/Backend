package com.jinryomate.backend.global.web;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/health")
    public Health health() {
        return new Health(applicationName, "UP", OffsetDateTime.now());
    }

    public record Health(String application, String status, OffsetDateTime time) {}
}
