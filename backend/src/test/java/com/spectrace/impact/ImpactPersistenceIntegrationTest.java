package com.spectrace.impact;

import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactFindingClassification;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ImpactPersistenceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String CHANGE_X = "scrum62-change-x";
    private static final String CHANGE_Y = "scrum62-change-y";
    private static final String RUN_X = "scrum62-run-x";
    private static final String RUN_Y = "scrum62-run-y";
    private static final String FINDING_X = "scrum62-finding-x";
    private static final String FINDING_Y = "scrum62-finding-y";
    private static final String REVIEW_X = "scrum62-review-x";

    @Autowired
    private ImpactAnalysisRunRepository runs;
    @Autowired
    private ImpactFindingRepository findings;
    @Autowired
    private ReviewTaskLinkageRepository reviewTasks;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareFixtures() {
        cleanUp();
        insertChangeRequest(CHANGE_X, "CR-SCRUM-62-X");
        insertChangeRequest(CHANGE_Y, "CR-SCRUM-62-Y");
    }

    @AfterEach
    void removeFixtures() {
        cleanUp();
    }

    @Test
    void roundTripsRunFindingsAndReviewTaskLinkage() {
        ImpactAnalysisRun run = run(RUN_X, CHANGE_X, "impact-analysis:" + CHANGE_X);
        assertThat(runs.saveOrGetExisting(run)).isEqualTo(run);

        ImpactFinding first = finding(FINDING_X, run.impactAnalysisRunId(), "prod_usda_1106285",
                "formula_1106285_v1", "label_1106285_v1", ImpactFindingClassification.REVIEW_REQUIRED,
                List.of("SOY"));
        ImpactFinding second = finding(FINDING_Y, run.impactAnalysisRunId(), "prod_usda_1106963",
                "formula_1106963_v1", "label_1106963_v1", ImpactFindingClassification.NO_ACTION,
                List.of());
        findings.saveAll(run.impactAnalysisRunId(), List.of(first, second));

        ReviewTaskLinkage linkage = linkage(first.impactFindingId(), REVIEW_X);
        assertThat(reviewTasks.saveOrGetExisting(linkage)).isEqualTo(linkage);

        assertThat(runs.findById(run.impactAnalysisRunId())).contains(run);
        assertThat(runs.findByChangeRequestId(CHANGE_X)).contains(run);
        assertThat(runs.findByIdempotencyKey(run.idempotencyKey())).contains(run);
        assertThat(findings.findByRunId(run.impactAnalysisRunId())).containsExactly(first, second);
        assertThat(reviewTasks.findByFindingId(first.impactFindingId())).contains(linkage);
    }

    @Test
    void sameChangeRequestReturnsExistingRunWithoutCreatingDuplicates() {
        ImpactAnalysisRun first = run(RUN_X, CHANGE_X, "impact-analysis:" + CHANGE_X);
        ImpactAnalysisRun second = run("scrum62-run-x-retry", CHANGE_X, "retry-key-with-different-correlation-id");

        assertThat(runs.saveOrGetExisting(first)).isEqualTo(first);
        assertThat(runs.saveOrGetExisting(second)).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_analysis_run WHERE change_request_id = ?",
                Integer.class,
                CHANGE_X
        )).isEqualTo(1);

        ImpactFinding finding = finding(FINDING_X, first.impactAnalysisRunId(), "prod_usda_1106285",
                "formula_1106285_v1", "label_1106285_v1", ImpactFindingClassification.REVIEW_REQUIRED,
                List.of("SOY"));
        findings.saveAll(first.impactAnalysisRunId(), List.of(finding));
        assertThat(reviewTasks.saveOrGetExisting(linkage(finding.impactFindingId(), REVIEW_X)))
                .isEqualTo(linkage(finding.impactFindingId(), REVIEW_X));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_finding WHERE impact_analysis_run_id = ?",
                Integer.class,
                first.impactAnalysisRunId()
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE impact_finding_id = ?",
                Integer.class,
                finding.impactFindingId()
        )).isEqualTo(1);
    }

    @Test
    void differentChangeRequestsCreateIndependentRuns() {
        ImpactAnalysisRun first = run(RUN_X, CHANGE_X, "impact-analysis:" + CHANGE_X);
        ImpactAnalysisRun second = run(RUN_Y, CHANGE_Y, "impact-analysis:" + CHANGE_Y);

        assertThat(runs.saveOrGetExisting(first)).isEqualTo(first);
        assertThat(runs.saveOrGetExisting(second)).isEqualTo(second);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM impact_analysis_run WHERE impact_analysis_run_id IN (?, ?)",
                Integer.class,
                RUN_X,
                RUN_Y
        )).isEqualTo(2);
    }

    @Test
    void reviewTaskLinkageReturnsExistingLinkForSameFinding() {
        ImpactAnalysisRun run = run(RUN_X, CHANGE_X, "impact-analysis:" + CHANGE_X);
        runs.saveOrGetExisting(run);
        findings.saveAll(run.impactAnalysisRunId(), List.of(finding(
                FINDING_X, run.impactAnalysisRunId(), "prod_usda_1106285",
                "formula_1106285_v1", "label_1106285_v1", ImpactFindingClassification.REVIEW_REQUIRED,
                List.of("SOY"))));

        ReviewTaskLinkage first = linkage(FINDING_X, REVIEW_X);
        ReviewTaskLinkage retry = linkage(FINDING_X, "scrum62-review-retry");
        assertThat(reviewTasks.saveOrGetExisting(first)).isEqualTo(first);
        assertThat(reviewTasks.saveOrGetExisting(retry)).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE impact_finding_id = ?",
                Integer.class,
                FINDING_X
        )).isEqualTo(1);
    }

    private ImpactAnalysisRun run(String runId, String changeRequestId, String idempotencyKey) {
        return new ImpactAnalysisRun(
                runId,
                "code-" + runId,
                changeRequestId,
                "ruleset_us_falcpa_demo_v1",
                ImpactRunStatus.COMPLETED,
                Instant.parse("2026-09-28T00:00:00Z"),
                Instant.parse("2026-09-28T00:00:01Z"),
                "user_label_officer",
                "prov_project_seed",
                idempotencyKey
        );
    }

    private ImpactFinding finding(
            String findingId,
            String runId,
            String productId,
            String formulaId,
            String labelId,
            ImpactFindingClassification classification,
            List<String> missingCodes
    ) {
        return new ImpactFinding(
                findingId,
                runId,
                productId,
                formulaId,
                formulaId,
                labelId,
                classification,
                missingCodes,
                classification == ImpactFindingClassification.REVIEW_REQUIRED
                        ? "Soy coverage requires review"
                        : "Existing label coverage is sufficient",
                "prov_project_seed"
        );
    }

    private ReviewTaskLinkage linkage(String findingId, String reviewTaskId) {
        return new ReviewTaskLinkage(
                reviewTaskId,
                findingId,
                "prod_usda_1106285",
                "label_1106285_v1",
                null,
                ReviewTaskStatus.OPEN,
                "user_approver",
                "user_label_officer",
                Instant.parse("2026-09-28T00:00:00Z"),
                "prov_project_seed"
        );
    }

    private void insertChangeRequest(String id, String code) {
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
                id,
                code,
                "SCRUM-62 persistence fixture " + id
        );
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM review_task WHERE review_task_id LIKE 'scrum62-%'");
        jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id LIKE 'scrum62-%'");
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id LIKE 'scrum62-%'");
        jdbc.update("DELETE FROM change_request WHERE change_request_id LIKE 'scrum62-%'");
    }
}
