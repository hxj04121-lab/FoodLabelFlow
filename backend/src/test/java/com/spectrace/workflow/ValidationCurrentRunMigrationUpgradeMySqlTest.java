package com.spectrace.workflow;

import com.spectrace.workflow.infrastructure.JdbcLabelReviewCommandRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** A real forward V9 upgrade; historical ties are deliberately unknowable and are never guessed. */
@Testcontainers
class ValidationCurrentRunMigrationUpgradeMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("spectrace_test").withPassword("spectrace_test_password");

    @Test
    void uniqueHistoricalMaximumBootstrapsWhileAmbiguousTiesFailClosedUntilARecordedNewRun() throws Exception {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("9").load().migrate();
        String unique = cloneLabel(jdbc, "order_upgrade_unique", "prod_usda_1106285");
        String ambiguous = cloneLabel(jdbc, "order_upgrade_ambiguous", "prod_usda_1106963");
        String allPassTie = cloneLabel(jdbc, "order_upgrade_all_pass_tie", "prod_usda_1107123");
        insertRun(jdbc, "ffffffff_unique_old_fail", unique, "FAILED", "2026-10-08 11:59:59");
        insertRun(jdbc, "11111111_unique_new_pass", unique, "PASSED", "2026-10-08 12:00:00");
        insertRun(jdbc, "ffffffff_ambiguous_old_pass", ambiguous, "PASSED", "2026-10-08 12:00:00");
        insertRun(jdbc, "11111111_ambiguous_new_fail", ambiguous, "FAILED", "2026-10-08 12:00:00");
        insertRun(jdbc, "ffffffff_all_pass_tie", allPassTie, "PASSED", "2026-10-08 12:00:00");
        insertRun(jdbc, "11111111_all_pass_tie", allPassTie, "PASSED", "2026-10-08 12:00:00");
        var historyBefore = S3CompoundMakerFixture.allRows(jdbc);
        String oldHistorySha = sha(JsonMapper.builder().build().writeValueAsString(historyBefore));
        Flyway.configure().dataSource(dataSource).load().migrate();
        var historyAfterUpgrade = S3CompoundMakerFixture.allRows(jdbc);
        assertThat(historyAfterUpgrade).as("V10 cannot rewrite any original business or identity row").isEqualTo(historyBefore);
        assertThat(sha(JsonMapper.builder().build().writeValueAsString(historyAfterUpgrade))).isEqualTo(oldHistorySha);
        var legacyRegistrations = jdbc.queryForList("SELECT * FROM validation_run_registration ORDER BY 1,2");
        assertThat(legacyRegistrations).hasSize(historyBefore.get("validation_run").size())
                .allSatisfy(registration -> {
                    assertThat(registration.get("origin")).isEqualTo("LEGACY");
                    assertThat(registration.get("registration_sequence")).as("migration must not manufacture a causal sequence").isNull();
                });
        assertThat(current(jdbc, unique)).containsEntry("validation_run_id", "11111111_unique_new_pass")
                .containsEntry("origin", "LEGACY_UNIQUE");
        assertThat(((Number) current(jdbc, unique).get("current_sequence")).longValue()).isZero();
        assertThat(((Number) current(jdbc, unique).get("observed_run_count")).longValue()).isEqualTo(2L);
        for (String tied : List.of(ambiguous, allPassTie)) {
            assertThat(current(jdbc, tied)).containsEntry("validation_run_id", null).containsEntry("origin", "LEGACY_AMBIGUOUS");
            assertThat(((Number) current(jdbc, tied).get("current_sequence")).longValue()).isZero();
            SQLException error;
            try (var connection = dataSource.getConnection();
                 var call = connection.prepareCall("{call sp_assert_current_validation_pass(?)}")) {
                call.setString(1, tied); error = catchThrowableOfType(call::execute, SQLException.class);
            }
            assertThat((Throwable) error).isNotNull();
            assertThat(error.getSQLState()).isEqualTo("45000");
        }
        var repository = new JdbcLabelReviewCommandRepository(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThat(transaction.<Boolean>execute(status -> repository.hasPassingValidation(unique, rule(jdbc, unique)))).isTrue();
        assertThat(transaction.<Boolean>execute(status -> repository.hasPassingValidation(ambiguous, rule(jdbc, ambiguous)))).isFalse();
        assertThat(transaction.<Boolean>execute(status -> repository.hasPassingValidation(allPassTie, rule(jdbc, allPassTie)))).isFalse();
        var ambiguousBeforeNewRun = current(jdbc, ambiguous);
        // A later actual committed insert is authoritative even if its wall clock moved backward.
        transaction.executeWithoutResult(ignored -> jdbc.update("CALL sp_insert_validation_run(?,?,?,?,?,?,?,?)",
                "00000000_new_recorded_pass", ambiguous, rule(jdbc, ambiguous), "PASSED", "user_label_officer",
                "2020-01-01 00:00:00", "Actually committed canonical new run after upgrade",
                jdbc.queryForObject("SELECT data_provenance_id FROM label_version WHERE label_version_id=?", String.class, ambiguous)));
        assertThat(current(jdbc, ambiguous)).containsEntry("validation_run_id", "00000000_new_recorded_pass")
                .containsEntry("origin", "RECORDED");
        assertThat(((Number) current(jdbc, ambiguous).get("current_sequence")).longValue()).isEqualTo(1L);
        assertThat(((Number) current(jdbc, ambiguous).get("observed_run_count")).longValue()).isEqualTo(3L);
        var newRegistration = jdbc.queryForMap("SELECT * FROM validation_run_registration WHERE validation_run_id='00000000_new_recorded_pass'");
        assertThat(newRegistration).containsEntry("origin", "RECORDED");
        assertThat(((Number) newRegistration.get("registration_sequence")).longValue()).isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT * FROM validation_run_registration ORDER BY 1,2")).containsAll(legacyRegistrations);
        assertThat(transaction.<Boolean>execute(status -> repository.hasPassingValidation(ambiguous, rule(jdbc, ambiguous)))).isTrue();
        var runsAfter = jdbc.queryForList("SELECT * FROM validation_run ORDER BY 1,2");
        assertThat(runsAfter).containsAll(historyBefore.get("validation_run"));
        for (String table : historyBefore.keySet()) {
            if (!table.equals("validation_run")) assertThat(S3CompoundMakerFixture.allRows(jdbc).get(table)).isEqualTo(historyBefore.get(table));
        }
        String output = System.getProperty("validation.order.upgrade.evidence");
        if (output != null) {
            var proof = new LinkedHashMap<String, Object>();
            proof.put("containerId", MYSQL.getContainerId());
            proof.put("mysql", jdbc.queryForMap("SELECT VERSION() AS version"));
            proof.put("historyBeforeSha256", oldHistorySha);
            proof.put("historyAfterUpgradeSha256", sha(JsonMapper.builder().build().writeValueAsString(historyAfterUpgrade)));
            proof.put("allLegacyRegistrationsHaveNullSequence", true);
            proof.put("legacyRegistrationCount", legacyRegistrations.size());
            proof.put("legacyRegistrationSha256", sha(JsonMapper.builder().build().writeValueAsString(legacyRegistrations)));
            proof.put("uniqueHistoricalMaximum", current(jdbc, unique));
            proof.put("ambiguousHistoricalMaximumBeforeNewRun", ambiguousBeforeNewRun);
            proof.put("allPassedSameSecondStillAmbiguous", current(jdbc, allPassTie));
            proof.put("freshRecordedRunAfterBackwardClock", current(jdbc, ambiguous));
            proof.put("freshRunRegistration", newRegistration);
            proof.put("oldRowsUnchanged", true);
            Files.writeString(Path.of(output), JsonMapper.builder().build().writeValueAsString(proof), StandardCharsets.UTF_8);
        }
    }

    private static String cloneLabel(JdbcTemplate jdbc, String label, String product) {
        assertThat(jdbc.update("""
                INSERT INTO label_version(label_version_id,product_id,formula_version_id,rule_set_version_id,jurisdiction_code,
                    version_number,raw_ingredient_text,lifecycle_status,is_current_published,created_by_user_id,created_at,data_provenance_id)
                SELECT ?,p.product_id,p.current_formula_version_id,lv.rule_set_version_id,lv.jurisdiction_code,
                    lv.version_number+1,lv.raw_ingredient_text,'DRAFT','N','user_label_officer',NOW(),lv.data_provenance_id
                FROM product p JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id WHERE p.product_id=?
                """, label, product)).isEqualTo(1);
        return label;
    }

    private static void insertRun(JdbcTemplate jdbc, String id, String label, String status, String ranAt) {
        assertThat(jdbc.update("""
                INSERT INTO validation_run(validation_run_id,label_version_id,rule_set_version_id,status,ran_by_user_id,ran_at,summary,data_provenance_id)
                SELECT ?,label_version_id,rule_set_version_id,?,'user_label_officer',CAST(? AS DATETIME),
                    'Private historical upgrade input',data_provenance_id FROM label_version WHERE label_version_id=?
                """, id, status, ranAt, label)).isEqualTo(1);
    }

    private static Map<String, Object> current(JdbcTemplate jdbc, String label) {
        return jdbc.queryForMap("SELECT * FROM validation_current_run WHERE label_version_id=? AND rule_set_version_id=?", label, rule(jdbc, label));
    }

    private static String rule(JdbcTemplate jdbc, String label) {
        return jdbc.queryForObject("SELECT rule_set_version_id FROM label_version WHERE label_version_id=?", String.class, label);
    }

    private static String sha(String value) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
