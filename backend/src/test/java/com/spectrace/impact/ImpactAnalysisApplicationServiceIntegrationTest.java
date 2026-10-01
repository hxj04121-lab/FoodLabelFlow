package com.spectrace.impact;

import com.spectrace.impact.application.ImpactAnalysisApplicationService;
import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImpactAnalysisApplicationServiceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String CHANGE = "scrum59-change";
    private static final String RUN = "scrum59-run";
    private static final String REVIEW_FINDING = "scrum59-finding-review";
    private static final String NO_ACTION_FINDING = "scrum59-finding-no-action";
    private static final String REVIEW_TASK = "scrum59-review-task";

    @Autowired
    private ImpactAnalysisApplicationService service;
    @Autowired
    private ImpactAnalysisRunRepository runs;
    @Autowired
    private ImpactFindingRepository findings;
    @Autowired
    private ReviewTaskLinkageRepository reviewTasks;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
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
                CHANGE, "SCRUM-59-SOY", "SCRUM-59 atomic impact fixture");
    }

    @AfterEach
    void removeFixtures() {
        cleanUp();
    }

    @Test
    void commitsRunFindingsReviewLinkAndAuditAsOneCompleteUnit() {
        ImpactAnalysisRun run = run();
        ImpactFinding review = finding(REVIEW_FINDING, run, ImpactClassification.REVIEW_REQUIRED, List.of("SOY"));
        ImpactFinding noAction = finding(
                NO_ACTION_FINDING, run, ImpactClassification.NO_ACTION, List.of(),
                "prod_usda_1106963", "formula_1106963_v1", "label_1106963_v1");

        assertThat(service.execute(run, List.of(review, noAction), List.of(linkage(review)))).isEqualTo(run);

        assertThat(runs.findById(RUN)).contains(run);
        assertThat(findings.findByRunId(RUN)).containsExactly(review, noAction);
        assertThat(reviewTasks.findByFindingId(REVIEW_FINDING)).isPresent();
        assertThat(reviewTasks.findByFindingId(NO_ACTION_FINDING)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ?", Integer.class, RUN)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT entity_id FROM audit_event WHERE correlation_id = ?", String.class, RUN)).isEqualTo(RUN);
        assertThat(jdbc.queryForObject(
                "SELECT actor_user_id FROM audit_event WHERE correlation_id = ?", String.class, RUN))
                .isEqualTo("user_label_officer");
        assertThat(jdbc.queryForObject(
                "SELECT data_provenance_id FROM audit_event WHERE correlation_id = ?", String.class, RUN))
                .isEqualTo("prov_project_seed");
        assertThat(jdbc.queryForObject(
                "SELECT event_type FROM audit_event WHERE correlation_id = ?", String.class, RUN))
                .isEqualTo("IMPACT_ANALYSIS");
    }

    @Test
    void sequentialReplayWithFreshIdentifiersReturnsTheOriginalWithoutWritingAnything() {
        ImpactAnalysisRun original = executeReviewRun(run(), "");
        ImpactAnalysisRun replay = run(RUN + "-retry", "ruleset_us_falcpa_demo_v1");

        assertThat(executeReviewRun(replay, "-retry")).isEqualTo(original);
        assertSingleResult(original);
        assertThat(runs.findById(replay.impactAnalysisRunId())).isEmpty();
        assertThat(reviewTasks.findByFindingId(REVIEW_FINDING + "-retry")).isEmpty();
    }

    @Test
    void anotherRuleSetConflictsWithoutChangingTheExistingResult() {
        ImpactAnalysisRun original = executeReviewRun(run(), "");
        ImpactAnalysisRun conflict = run(RUN + "-retry", "ruleset_us_falcpa_demo_v2");

        assertThatThrownBy(() -> executeReviewRun(conflict, "-retry"))
                .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(409);
                    assertThat(failure.code()).isEqualTo("DATA_CONFLICT");
                });
        assertSingleResult(original);
    }

    @Test
    void replaySeesACommittedWinnerEvenAfterAnOlderRepeatableReadSnapshot() throws Exception {
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ImpactAnalysisRun> replay = executor.submit(() -> repeatableRead().execute(status -> {
                assertThat(runs.findByChangeRequestId(CHANGE)).isEmpty();
                snapshotReady.countDown();
                await(winnerCommitted);
                return executeReviewRun(run(RUN + "-retry", "ruleset_us_falcpa_demo_v1"), "-retry");
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            ImpactAnalysisRun original = executeReviewRun(run(), "");
            winnerCommitted.countDown();

            assertThat(replay.get(10, TimeUnit.SECONDS)).isEqualTo(original);
            assertSingleResult(original);
        } finally {
            winnerCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousTriggersCommitOneRunFindingTaskAndAudit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ImpactAnalysisRun> first = executor.submit(() -> repeatableRead().execute(status -> {
                assertThat(runs.findByChangeRequestId(CHANGE)).isEmpty();
                ready.countDown();
                await(start);
                return executeReviewRun(run(), "");
            }));
            Future<ImpactAnalysisRun> second = executor.submit(() -> repeatableRead().execute(status -> {
                assertThat(runs.findByChangeRequestId(CHANGE)).isEmpty();
                ready.countDown();
                await(start);
                return executeReviewRun(run(RUN + "-retry", "ruleset_us_falcpa_demo_v1"), "-retry");
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ImpactAnalysisRun winner = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(winner);
            assertSingleResult(winner);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private ImpactAnalysisRun executeReviewRun(ImpactAnalysisRun run, String suffix) {
        ImpactFinding review = finding(REVIEW_FINDING + suffix, run,
                ImpactClassification.REVIEW_REQUIRED, List.of("SOY"));
        return service.execute(run, List.of(review), List.of(linkage(review, REVIEW_TASK + suffix)));
    }

    private void assertSingleResult(ImpactAnalysisRun winner) {
        assertThat(runs.findByChangeRequestId(CHANGE)).containsExactly(winner);
        assertThat(findings.findByRunId(winner.impactAnalysisRunId())).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM review_task rt
                JOIN impact_finding f ON f.impact_finding_id = rt.impact_finding_id
                JOIN impact_analysis_run r ON r.impact_analysis_run_id = f.impact_analysis_run_id
                WHERE r.change_request_id = ?
                """, Integer.class, CHANGE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE correlation_id LIKE 'scrum59-run%'
                """, Integer.class)).isEqualTo(1);
    }

    private TransactionTemplate repeatableRead() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other trigger");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the other trigger", interrupted);
        }
    }

    private ImpactAnalysisRun run() {
        return run(RUN, "ruleset_us_falcpa_demo_v1");
    }

    private ImpactAnalysisRun run(String runId, String ruleSetId) {
        Instant started = Instant.parse("2026-10-01T00:00:00Z");
        return new ImpactAnalysisRun(
                runId, "code-" + runId, CHANGE, ruleSetId, ImpactRunStatus.COMPLETED,
                started, started.plusSeconds(1), "user_label_officer", "prov_project_seed");
    }

    private ImpactFinding finding(
            String id, ImpactAnalysisRun run, ImpactClassification classification, List<String> missingCodes
    ) {
        return finding(id, run, classification, missingCodes,
                "prod_usda_1106285", "formula_1106285_v1", "label_1106285_v1");
    }

    private ImpactFinding finding(
            String id, ImpactAnalysisRun run, ImpactClassification classification, List<String> missingCodes,
            String productId, String formulaId, String labelId
    ) {
        return new ImpactFinding(
                id, run.impactAnalysisRunId(), productId, formulaId, null,
                labelId, classification, missingCodes,
                classification == ImpactClassification.REVIEW_REQUIRED
                        ? "Soy coverage requires review" : "Existing label coverage is sufficient",
                "prov_project_seed");
    }

    private ReviewTaskLinkage linkage(ImpactFinding finding) {
        return linkage(finding, REVIEW_TASK);
    }

    private ReviewTaskLinkage linkage(ImpactFinding finding, String taskId) {
        return new ReviewTaskLinkage(
                taskId, finding.impactFindingId(), finding.productId(), finding.currentLabelVersionId(), null,
                ReviewTaskStatus.OPEN, "user_approver", "user_label_officer",
                Instant.parse("2026-10-01T00:00:00Z"), "prov_project_seed");
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM audit_event WHERE correlation_id LIKE 'scrum59-run%'");
        jdbc.update("DELETE FROM review_task WHERE review_task_id LIKE 'scrum59-review-task%'");
        jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id LIKE 'scrum59-finding-%'");
        jdbc.update("DELETE FROM impact_analysis_run WHERE change_request_id = ?", CHANGE);
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", CHANGE);
    }
}
