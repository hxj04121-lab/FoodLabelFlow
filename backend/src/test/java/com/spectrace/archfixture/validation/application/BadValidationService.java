package com.spectrace.archfixture.validation.application;

import com.spectrace.archfixture.foreign.infrastructure.ForeignAdapter;
import org.springframework.jdbc.core.JdbcTemplate;

public class BadValidationService {
    public BadValidationService(ForeignAdapter adapter, JdbcTemplate jdbcTemplate) {
    }
}
