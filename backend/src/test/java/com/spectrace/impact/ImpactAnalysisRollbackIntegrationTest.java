package com.spectrace.impact;

import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.audit.application.port.ImpactAuditEventPort;
import com.spectrace.impact.application.ImpactAnalysisApplicationService;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import com.spectrace.impact.infrastructure.JdbcImpactAnalysisRunRepository;
import com.spectrace.impact.infrastructure.JdbcImpactFindingRepository;
import com.spectrace.impact.infrastructure.JdbcReviewTaskLinkageRepository;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(ImpactAnalysisRollbackIntegrationTest.FailureInjectionConfiguration.class)
class ImpactAnalysisRollbackIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String CHANGE = "scrum61-rollback-change";
    private static final String RUN = "scrum61-rollback-run";
    private static final String FINDING_ONE = "scrum61-rollback-finding-one";
    private static final String FINDING_MIDDLE = "scrum61-rollback-finding-middle";
    private static final String FINDING_LAST = "scrum61-rollback-finding-last";
    private static final String REVIEW_TASK = "scrum61-rollback-review-task";
    private static final List<String> FINDING_IDS = List.of(FINDING_ONE, FINDING_MIDDLE, FINDING_LAST);

    @Autowired
    private ImpactAnalysisApplicationService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FailurePoint failurePoint;

    @BeforeEach
    void prepareFixtures() {
        cleanUp();
        jdbc.update(
                """
                INSERT INTO change_request(
                  change_request_id, change_request_code, change_type, status,
                  requested_at, requested_by_user_id, description,
                  from_formula_version_id, to_formula_version_id, data_provenance_id
                )
                SELECT ?, ?, 'FORMULA', 'ANALYZED', UTC_TIMESTAMP(), 'user_label_officer', ?,
                       formula_version_id, formula_version_id, data_provenance_id
                FROM label_version
                WHERE label_version_id = 'label_1106285_v1'
                """,
                CHANGE, "SCRUM-61-ROLLBACK", "SCRUM-61 rollback fixture");
        failurePoint.disable();
    }

    @AfterEach
    void removeFixtures() {
        failurePoint.disable();
        cleanUp();
    }

    @Test
    void runInsertFailureRollsBackAndSameRequestRetries() {
        rollsBackAndRetries(Failure.RUN_INSERT);
    }

    @Test
    void middleFindingInsertFailureRollsBackPriorFindingAndSameRequestRetries() {
        rollsBackAndRetries(Failure.MIDDLE_FINDING_INSERT);
    }

    @Test
    void reviewTaskWriteFailureRollsBackAndSameRequestRetries() {
        rollsBackAndRetries(Failure.REVIEW_TASK_WRITE);
    }

    @Test
    void auditWriteFailureRollsBackAndSameRequestRetries() {
        rollsBackAndRetries(Failure.AUDIT_EVENT_WRITE);
    }

    private void rollsBackAndRetries(Failure failure) {
        Counts baseline = counts();
        ImpactAnalysisRun run = run();
        List<ImpactFinding> findings = findings();
        List<ReviewTaskLinkage> reviewTasks = List.of(linkage());

        failurePoint.failAt(failure);
        assertThatThrownBy(() -> service.execute(run, findings, reviewTasks))
                .isInstanceOf(InjectedTestFailureException.class)
                .hasMessage("injected failure at " + failure);
        assertNoExecutionResidue();
        assertThat(counts()).isEqualTo(baseline);

        failurePoint.disable();
        assertThat(service.execute(run, findings, reviewTasks)).isEqualTo(run);

        assertThat(counts()).isEqualTo(new Counts(
                baseline.runs + 1,
                baseline.findings + FINDING_IDS.size(),
                baseline.reviewTasks + 1,
                baseline.audits + 1));
        assertSuccessfulExecution(run);
    }

    private void assertNoExecutionResidue() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_analysis_run WHERE impact_analysis_run_id = ? OR change_request_id = ? "
                        + "OR idempotency_key = ?",
                Integer.class, RUN, CHANGE, "impact-analysis:" + CHANGE)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_finding WHERE impact_analysis_run_id = ? OR impact_finding_id IN (?, ?, ?)",
                Integer.class, RUN, FINDING_ONE, FINDING_MIDDLE, FINDING_LAST)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id = ? OR impact_finding_id IN (?, ?, ?)",
                Integer.class, REVIEW_TASK, FINDING_ONE, FINDING_MIDDLE, FINDING_LAST)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ? OR entity_id = ?",
                Integer.class, RUN, RUN)).isZero();
    }

    private void assertSuccessfulExecution(ImpactAnalysisRun run) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_analysis_run WHERE change_request_id = ?",
                Integer.class, CHANGE)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_analysis_run WHERE idempotency_key = ?",
                Integer.class, "impact-analysis:" + CHANGE)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_finding WHERE impact_analysis_run_id = ?",
                Integer.class, RUN)).isEqualTo(FINDING_IDS.size());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_finding WHERE impact_finding_id IN (?, ?, ?)",
                Integer.class, FINDING_ONE, FINDING_MIDDLE, FINDING_LAST)).isEqualTo(FINDING_IDS.size());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE impact_finding_id = ?",
                Integer.class, FINDING_ONE)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id = ?",
                Integer.class, REVIEW_TASK)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ? AND entity_id = ? "
                        + "AND event_type = 'IMPACT_ANALYSIS'",
                Integer.class, RUN, RUN)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE entity_id = ?",
                Integer.class, RUN)).isEqualTo(1);
    }

    private Counts counts() {
        return new Counts(
                count("impact_analysis_run"),
                count("impact_finding"),
                count("review_task"),
                count("audit_event"));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private ImpactAnalysisRun run() {
        Instant started = Instant.parse("2026-10-01T00:00:00Z");
        return new ImpactAnalysisRun(
                RUN, "code-" + RUN, CHANGE, "ruleset_us_falcpa_demo_v1", ImpactRunStatus.COMPLETED,
                started, started.plusSeconds(1), "user_label_officer", "prov_project_seed");
    }

    private List<ImpactFinding> findings() {
        return List.of(
                finding(FINDING_ONE, "prod_usda_1106285", "formula_1106285_v1", "label_1106285_v1",
                        ImpactClassification.REVIEW_REQUIRED, List.of("SOY")),
                finding(FINDING_MIDDLE, "prod_usda_1106963", "formula_1106963_v1", "label_1106963_v1",
                        ImpactClassification.NO_ACTION, List.of()),
                finding(FINDING_LAST, "prod_usda_1106980", "formula_1106980_v1", "label_1106980_v1",
                        ImpactClassification.NO_ACTION, List.of()));
    }

    private ImpactFinding finding(
            String id, String productId, String formulaId, String labelId,
            ImpactClassification classification, List<String> missingCodes
    ) {
        return new ImpactFinding(
                id, RUN, productId, formulaId, null, labelId, classification, missingCodes,
                classification == ImpactClassification.REVIEW_REQUIRED
                        ? "Soy coverage requires review" : "Existing label coverage is sufficient",
                "prov_project_seed");
    }

    private ReviewTaskLinkage linkage() {
        return new ReviewTaskLinkage(
                REVIEW_TASK, FINDING_ONE, "prod_usda_1106285", "label_1106285_v1", null,
                ReviewTaskStatus.OPEN, "user_approver", "user_label_officer",
                Instant.parse("2026-10-01T00:00:00Z"), "prov_project_seed");
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM audit_event WHERE correlation_id = ? OR entity_id = ?", RUN, RUN);
        jdbc.update("DELETE FROM review_task WHERE review_task_id = ? OR impact_finding_id IN (?, ?, ?)",
                REVIEW_TASK, FINDING_ONE, FINDING_MIDDLE, FINDING_LAST);
        jdbc.update("DELETE FROM impact_finding WHERE impact_analysis_run_id = ? OR impact_finding_id IN (?, ?, ?)",
                RUN, FINDING_ONE, FINDING_MIDDLE, FINDING_LAST);
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ? OR change_request_id = ?",
                RUN, CHANGE);
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", CHANGE);
    }

    private record Counts(int runs, int findings, int reviewTasks, int audits) { }

    private enum Failure { RUN_INSERT, MIDDLE_FINDING_INSERT, REVIEW_TASK_WRITE, AUDIT_EVENT_WRITE }

    private static final class InjectedTestFailureException extends RuntimeException {
        private InjectedTestFailureException(Failure failure) {
            super("injected failure at " + failure);
        }
    }

    static class FailurePoint {
        private final AtomicReference<Failure> current = new AtomicReference<>();

        void failAt(Failure failure) {
            current.set(failure);
        }

        void disable() {
            current.set(null);
        }

        void failIf(Failure failure) {
            if (current.compareAndSet(failure, null)) {
                throw new InjectedTestFailureException(failure);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {
        @Bean
        FailurePoint failurePoint() {
            return new FailurePoint();
        }

        @Bean
        @Primary
        ImpactAnalysisRunRepository failingRunRepository(
                JdbcImpactAnalysisRunRepository delegate, FailurePoint failurePoint
        ) {
            return new ImpactAnalysisRunRepository() {
                @Override
                public void save(ImpactAnalysisRun run) {
                    delegate.save(run);
                    failurePoint.failIf(Failure.RUN_INSERT);
                }

                @Override
                public java.util.Optional<ImpactAnalysisRun> findById(String id) {
                    return delegate.findById(id);
                }

                @Override
                public List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId) {
                    return delegate.findByChangeRequestId(changeRequestId);
                }
            };
        }

        @Bean
        @Primary
        ImpactFindingRepository failingFindingRepository(
                JdbcImpactFindingRepository delegate, FailurePoint failurePoint
        ) {
            return new ImpactFindingRepository() {
                @Override
                public void saveAll(String runId, List<ImpactFinding> findings) {
                    if (findings.size() >= 3 && failurePoint.current.get() == Failure.MIDDLE_FINDING_INSERT) {
                        delegate.saveAll(runId, findings.subList(0, 1));
                        failurePoint.failIf(Failure.MIDDLE_FINDING_INSERT);
                    }
                    delegate.saveAll(runId, findings);
                }

                @Override
                public List<ImpactFinding> findByRunId(String runId) {
                    return delegate.findByRunId(runId);
                }
            };
        }

        @Bean
        @Primary
        ReviewTaskLinkageRepository failingReviewTaskRepository(
                JdbcReviewTaskLinkageRepository delegate, FailurePoint failurePoint
        ) {
            return new ReviewTaskLinkageRepository() {
                @Override
                public ReviewTaskLinkage saveOrGetExisting(ReviewTaskLinkage linkage) {
                    ReviewTaskLinkage saved = delegate.saveOrGetExisting(linkage);
                    failurePoint.failIf(Failure.REVIEW_TASK_WRITE);
                    return saved;
                }

                @Override
                public java.util.Optional<ReviewTaskLinkage> findByFindingId(String findingId) {
                    return delegate.findByFindingId(findingId);
                }
            };
        }

        @Bean
        @Primary
        ImpactAuditEventPort failingAuditPort(
                AuditApplicationService delegate, FailurePoint failurePoint
        ) {
            return (actor, runId, changeRequestId, outcome, provenance) -> {
                delegate.recordImpactEvent(actor, runId, changeRequestId, outcome, provenance);
                failurePoint.failIf(Failure.AUDIT_EVENT_WRITE);
            };
        }
    }
}
