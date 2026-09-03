package com.spectrace;

import com.spectrace.identity.application.HealthApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthApplicationServiceTest {

    @Test
    void reportsHealthyDatabaseWhenSelectOneSucceeds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        HealthApplicationService.HealthStatus status = new HealthApplicationService(jdbcTemplate).health();

        assertThat(status.status()).isEqualTo("ok");
        assertThat(status.database()).isEqualTo("ok");
    }
}
