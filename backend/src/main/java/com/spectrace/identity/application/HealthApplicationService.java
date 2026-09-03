package com.spectrace.identity.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthApplicationService {

    private final JdbcTemplate jdbcTemplate;

    public HealthApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthStatus health() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new HealthStatus("ok", result != null && result == 1 ? "ok" : "unexpected");
    }

    public record HealthStatus(String status, String database) {
    }
}
