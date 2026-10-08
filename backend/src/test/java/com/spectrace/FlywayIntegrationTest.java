package com.spectrace;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayLoadsCurrentWorkflowMigrationsAndDatabaseHealthWorks() {
        Integer productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
        Integer impactCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM impact_finding", Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class
        );

        assertThat(productCount).isEqualTo(60);
        assertThat(impactCount).isZero();
        assertThat(migrationCount).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_task'
                  AND column_name IN (
                    'target_label_version_id', 'decision',
                    'resolved_by_user_id', 'resolved_at'
                  )
                """,
                Integer.class
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
