package com.spectrace.impact;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(ImpactAnalysisRollbackIntegrationTest.FailureAuditConfiguration.class)
class ImpactAnalysisRollbackIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String CHANGE = "scrum59-rollback-change";
    private static final String RUN = "scrum59-rollback-run";
    private static final String FINDING = "scrum59-rollback-finding";
    private static final String REVIEW_TASK = "scrum59-rollback-review-task";

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
                CHANGE, "SCRUM-59-ROLLBACK", "SCRUM-59 rollback fixture");
    }

    @AfterEach
    void removeFixtures() {
        cleanUp();
    }

    @Test
    void propagatesAuditFailureAndRollsBackEarlierWrites() {
        ImpactAnalysisRun run = run();
        ImpactFinding review = new ImpactFinding(
                FINDING, RUN, "prod_usda_1106285", "formula_1106285_v1", null,
                "label_1106285_v1", ImpactClassification.REVIEW_REQUIRED, List.of("SOY"),
                "Soy coverage requires review", "prov_project_seed");

        assertThatThrownBy(() -> service.execute(run, List.of(review), List.of(linkage())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("controlled audit failure");
        assertThat(runs.findById(RUN)).isEmpty();
        assertThat(findings.findByRunId(RUN)).isEmpty();
        assertThat(reviewTasks.findByFindingId(FINDING)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ?", Integer.class, RUN)).isEqualTo(0);
    }

    private ImpactAnalysisRun run() {
        Instant started = Instant.parse("2026-10-01T00:00:00Z");
        return new ImpactAnalysisRun(
                RUN, "code-" + RUN, CHANGE, "ruleset_us_falcpa_demo_v1", ImpactRunStatus.COMPLETED,
                started, started.plusSeconds(1), "user_label_officer", "prov_project_seed");
    }

    private ReviewTaskLinkage linkage() {
        return new ReviewTaskLinkage(
                REVIEW_TASK, FINDING, "prod_usda_1106285", "label_1106285_v1", null,
                ReviewTaskStatus.OPEN, "user_approver", "user_label_officer",
                Instant.parse("2026-10-01T00:00:00Z"), "prov_project_seed");
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM audit_event WHERE correlation_id = ?", RUN);
        jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", REVIEW_TASK);
        jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", FINDING);
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", RUN);
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", CHANGE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureAuditConfiguration {
        @Bean
        @Primary
        ImpactAuditEventPort failingAudit(JdbcTemplate jdbc) {
            return (actorId, runId, changeRequestId, outcome, provenanceId) -> {
                jdbc.update(
                        """
                        INSERT INTO audit_event(
                          audit_event_id, event_type, entity_type, entity_id, event_at,
                          actor_user_id, event_payload, correlation_id, data_provenance_id
                        ) VALUES (?, 'IMPACT_ANALYSIS', 'IMPACT_ANALYSIS_RUN', ?, UTC_TIMESTAMP(), ?,
                                  JSON_OBJECT(), ?, ?)
                        """,
                        UUID.randomUUID().toString(), runId, actorId, runId, provenanceId);
                throw new IllegalStateException("controlled audit failure");
            };
        }
    }
}
