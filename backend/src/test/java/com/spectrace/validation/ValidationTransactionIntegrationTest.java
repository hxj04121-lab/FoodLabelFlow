package com.spectrace.validation;

import com.spectrace.audit.application.port.AuditEventPort;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.validation.application.ValidationPersistenceService;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(ValidationTransactionIntegrationTest.FailureInjectionConfiguration.class)
class ValidationTransactionIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String LABEL = "label_1106285_v1";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String ACTOR = "user_label_officer";
    private static final String PROVENANCE = "prov_project_seed";
    private static final String RUN_PREFIX = "scrum31-";

    @Autowired
    private ValidationPersistenceService persistence;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailingRun failingRun;

    @Autowired
    private FailingResults failingResults;

    @Autowired
    private FailingAudit failingAudit;

    @Autowired
    @Qualifier("auditApplicationService")
    private AuditEventPort auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetFailureInjection() {
        failingRun.fail = false;
        failingResults.failAt = -1;
        failingAudit.fail = false;
    }

    @AfterEach
    void cleanRows() {
        jdbc.update("DELETE FROM audit_event WHERE correlation_id LIKE ?", RUN_PREFIX + "%");
        jdbc.update("DELETE FROM validation_result WHERE validation_run_id LIKE ?", RUN_PREFIX + "%");
        jdbc.update("DELETE FROM validation_run WHERE validation_run_id LIKE ?", RUN_PREFIX + "%");
    }

    @Test
    void passedValidationCommitsRunAllResultsAndTraceableAudit() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.PASSED, "all checks passed");
        List<ValidationResult> results = results(runId, false);
        Counts before = counts();

        persistence.persist(run, results);

        assertThat(counts()).isEqualTo(before.plus(1, results.size(), 1));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM validation_run WHERE validation_run_id = ?", String.class, runId))
                .isEqualTo("PASSED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM validation_result WHERE validation_run_id = ?", Integer.class, runId))
                .isEqualTo(results.size());
        assertAudit(run, "PASSED", "all checks passed");
    }

    @Test
    void failedValidationCommitsAsBusinessOutcomeNotDatabaseFailure() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.FAILED, "one blocking finding");
        List<ValidationResult> results = results(runId, true);

        persistence.persist(run, results);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM validation_run WHERE validation_run_id = ?", String.class, runId))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM validation_result WHERE validation_run_id = ?", Integer.class, runId))
                .isEqualTo(3);
        assertAudit(run, "FAILED", "one blocking finding");
    }

    @Test
    void runInsertFailureRollsBackEverythingToTheBaseline() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.PASSED, "run failure");
        List<ValidationResult> results = results(runId, false);
        Counts before = counts();
        failingRun.fail = true;

        assertThatThrownBy(() -> persistence.persist(run, results))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void firstMiddleAndLastResultFailuresRollBackEverything() {
        for (int failureIndex : List.of(0, 1, 2)) {
            String runId = runId();
            ValidationRun run = run(runId, ValidationStatus.PASSED, "result failure " + failureIndex);
            List<ValidationResult> results = results(runId, false);
            Counts before = counts();
            failingResults.failAt = failureIndex;

            assertThatThrownBy(() -> persistence.persist(run, results))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(counts()).as("failure index %s", failureIndex)
                    .isEqualTo(before);
            failingResults.failAt = -1;
        }
    }

    @Test
    void auditFailureRollsBackRunAndAllResults() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.PASSED, "audit failure");
        List<ValidationResult> results = results(runId, false);
        Counts before = counts();
        failingAudit.fail = true;

        assertThatThrownBy(() -> persistence.persist(run, results))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void auditRequiresAnOuterTransactionAndJoinsItWhenPresent() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.PASSED, "audit transaction");

        assertThatThrownBy(() -> auditService.recordValidationEvent(
                ACTOR, LABEL, runId, RULE_SET, PROVENANCE))
                .isInstanceOf(IllegalTransactionStateException.class);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                auditService.recordValidationEvent(
                        ACTOR, LABEL, runId, RULE_SET, PROVENANCE));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ?", Integer.class, runId))
                .isEqualTo(1);
    }

    @Test
    void rollbackLeavesNoResidueAndTheSameBusinessInputCanRetry() {
        String runId = runId();
        ValidationRun run = run(runId, ValidationStatus.PASSED, "retry");
        List<ValidationResult> results = results(runId, false);
        Counts before = counts();
        failingResults.failAt = 1;

        assertThatThrownBy(() -> persistence.persist(run, results))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counts()).isEqualTo(before);

        failingResults.failAt = -1;
        persistence.persist(run, results);
        assertThat(counts()).isEqualTo(before.plus(1, 3, 1));
        assertAudit(run, "PASSED", "retry");
    }

    private void assertAudit(ValidationRun run, String status, String summary) {
        var audit = jdbc.queryForMap(
                "SELECT event_type, entity_type, entity_id, actor_user_id, correlation_id, " +
                        "data_provenance_id, event_payload FROM audit_event WHERE correlation_id = ?",
                run.validationRunId());
        assertThat(audit).containsEntry("event_type", "LABEL_VALIDATION")
                .containsEntry("entity_type", "LABEL_VERSION")
                .containsEntry("entity_id", run.labelVersionId())
                .containsEntry("actor_user_id", run.ranByUserId())
                .containsEntry("correlation_id", run.validationRunId())
                .containsEntry("data_provenance_id", run.dataProvenanceId());
        assertThat(audit.get("event_payload").toString())
                .contains("\"validationRunId\": \"" + run.validationRunId() + "\"")
                .contains("\"labelVersionId\": \"" + run.labelVersionId() + "\"")
                .contains("\"ruleSetVersionId\": \"" + run.ruleSetVersionId() + "\"")
                .contains("\"status\": \"" + status + "\"")
                .contains("\"summary\": \"" + summary + "\"");
    }

    private Counts counts() {
        return new Counts(
                jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class));
    }

    private String runId() {
        return RUN_PREFIX + UUID.randomUUID();
    }

    private ValidationRun run(String runId, ValidationStatus status, String summary) {
        return new ValidationRun(
                runId, LABEL, RULE_SET, status, ACTOR,
                Instant.parse("2026-09-15T00:00:00Z"), summary, PROVENANCE);
    }

    private List<ValidationResult> results(String runId, boolean failed) {
        return List.of(
                new ValidationResult(runId + "-1", runId, "rule_v1_soy_ingredient",
                        "SOY_DECLARATION_PRESENT", failed ? ValidationSeverity.ERROR : ValidationSeverity.INFO,
                        !failed, failed, failed ? "Soy declaration is missing" : "Soy declaration is present"),
                new ValidationResult(runId + "-2", runId, "rule_v1_milk_ingredient",
                        "MILK_DECLARATION_PRESENT", ValidationSeverity.INFO, true, false, "Milk declaration is present"),
                new ValidationResult(runId + "-3", runId, "rule_v1_wheat_ingredient",
                        "WHEAT_DECLARATION_PRESENT", ValidationSeverity.INFO, true, false, "Wheat declaration is present"));
    }

    private record Counts(long runs, long results, long audits) {
        Counts plus(long runDelta, long resultDelta, long auditDelta) {
            return new Counts(runs + runDelta, results + resultDelta, audits + auditDelta);
        }
    }

    @TestConfiguration
    static class FailureInjectionConfiguration {
        @Bean
        @Primary
        FailingRun failingRun(
                @Qualifier("jdbcValidationRunRepository") ValidationRunRepository delegate) {
            return new FailingRun(delegate);
        }

        @Bean
        @Primary
        FailingResults failingResults(
                @Qualifier("jdbcValidationResultRepository") ValidationResultRepository delegate) {
            return new FailingResults(delegate);
        }

        @Bean
        @Primary
        FailingAudit failingAudit(
                @Qualifier("auditApplicationService") AuditEventPort delegate) {
            return new FailingAudit(delegate);
        }

    }

    static class FailingRun implements ValidationRunRepository {
        private final ValidationRunRepository delegate;
        volatile boolean fail;

        FailingRun(ValidationRunRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(ValidationRun run) {
            if (fail) throw new IllegalStateException("simulated run insert failure");
            delegate.save(run);
        }

        @Override
        public java.util.Optional<ValidationRun> findById(String id) {
            return delegate.findById(id);
        }
    }

    static class FailingResults implements ValidationResultRepository {
        private final ValidationResultRepository delegate;
        volatile int failAt = -1;

        FailingResults(ValidationResultRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void saveAll(String runId, List<ValidationResult> values) {
            if (failAt >= 0) {
                if (failAt > 0) delegate.saveAll(runId, values.subList(0, failAt));
                throw new IllegalStateException("simulated result failure at index " + failAt);
            }
            delegate.saveAll(runId, values);
        }

        @Override
        public List<ValidationResult> findByRunId(String runId) {
            return delegate.findByRunId(runId);
        }
    }

    static class FailingAudit implements AuditEventPort {
        private final AuditEventPort delegate;
        volatile boolean fail;

        FailingAudit(AuditEventPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void recordValidationEvent(
                String actorId, String labelVersionId, String validationRunId,
                String ruleSetVersionId, String provenanceId) {
            if (fail) throw new IllegalStateException("simulated audit failure");
            delegate.recordValidationEvent(
                    actorId, labelVersionId, validationRunId, ruleSetVersionId, provenanceId);
        }

        @Override
        public void recordValidationEvent(
                String actorId, String labelVersionId, String validationRunId,
                String ruleSetVersionId, String provenanceId, String status, String summary) {
            if (fail) throw new IllegalStateException("simulated audit failure");
            delegate.recordValidationEvent(
                    actorId, labelVersionId, validationRunId, ruleSetVersionId,
                    provenanceId, status, summary);
        }
    }
}
