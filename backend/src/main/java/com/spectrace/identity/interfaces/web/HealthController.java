package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.HealthApplicationService;
import com.spectrace.identity.interfaces.web.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthApplicationService healthApplicationService;

    public HealthController(HealthApplicationService healthApplicationService) {
        this.healthApplicationService = healthApplicationService;
    }

    @GetMapping
    public HealthResponse health() {
        HealthApplicationService.HealthStatus health = healthApplicationService.health();
        return new HealthResponse(health.status(), health.database());
    }
}
