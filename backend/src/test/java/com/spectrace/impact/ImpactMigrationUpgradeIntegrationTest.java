package com.spectrace.impact;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ImpactMigrationUpgradeIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @Test
    void upgradeFromAppliedV5PreservesRunsEvenWhenOldKeysSwapDuringBackfill() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("5").load().migrate();
        insertLegacyRun(jdbc, "upgrade-change-a", "upgrade-change-b", "upgrade-run-a");
        insertLegacyRun(jdbc, "upgrade-change-b", "upgrade-change-a", "upgrade-run-b");

        Flyway upgraded = Flyway.configure().dataSource(dataSource).load();
        upgraded.migrate();

        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM impact_analysis_run", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT idempotency_key FROM impact_analysis_run WHERE impact_analysis_run_id = 'upgrade-run-a'
                """, String.class)).isEqualTo("impact-analysis:upgrade-change-a");
        assertThat(jdbc.queryForObject("""
                SELECT idempotency_key FROM impact_analysis_run WHERE impact_analysis_run_id = 'upgrade-run-b'
                """, String.class)).isEqualTo("impact-analysis:upgrade-change-b");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO impact_analysis_run(
                    impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                    rule_set_version_id, status, started_at, completed_at, executed_by_user_id, data_provenance_id)
                SELECT 'upgrade-run-retry', 'new-run-code', change_request_id, 'new-key',
                       rule_set_version_id, status, started_at, completed_at, executed_by_user_id, data_provenance_id
                FROM impact_analysis_run WHERE impact_analysis_run_id = 'upgrade-run-a'
                """)).isInstanceOf(DuplicateKeyException.class);
    }

    private void insertLegacyRun(JdbcTemplate jdbc, String changeId, String runCode, String runId) {
        jdbc.update("""
                INSERT INTO change_request(
                    change_request_id, change_request_code, change_type, status, requested_at,
                    requested_by_user_id, description, from_formula_version_id, to_formula_version_id, data_provenance_id)
                VALUES (?, ?, 'FORMULA', 'ANALYZED', UTC_TIMESTAMP(), 'user_label_officer', 'V5 upgrade fixture',
                        'formula_1106285_v1', 'formula_1106285_v1', 'prov_project_seed')
                """, changeId, "code-" + changeId);
        jdbc.update("""
                INSERT INTO impact_analysis_run(
                    impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                    rule_set_version_id, status, started_at, completed_at, executed_by_user_id, data_provenance_id)
                VALUES (?, ?, ?, ?, 'ruleset_us_falcpa_demo_v1', 'COMPLETED', UTC_TIMESTAMP(), UTC_TIMESTAMP(),
                        'user_label_officer', 'prov_project_seed')
                """, runId, runCode, changeId, "impact-analysis:" + runCode);
    }
}
