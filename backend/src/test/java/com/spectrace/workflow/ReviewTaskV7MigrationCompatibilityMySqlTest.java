package com.spectrace.workflow;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
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

import javax.sql.DataSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class ReviewTaskV7MigrationCompatibilityMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace")
            .withUsername("spectrace_test")
            .withPassword("spectrace_test_password");

    @Test
    void migratesApprovedUnpublishedV6TaskIntoPublishableState() {
        DataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();

        String approvedLabel = createTaskFixture(
                jdbc, "v7_approved", "APPROVED", "APPROVE", "CLOSED", true);
        String rejectedLabel = createTaskFixture(
                jdbc, "v7_rejected", "REJECTED", "REJECT", "CLOSED", false);
        String publishedLabel = currentPublishedLabel(jdbc, "prod_usda_1107123");
        createTaskFixtureForExistingLabel(
                jdbc, "v7_published", publishedLabel, "APPROVE", "CLOSED");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertEquals("APPROVE", decision(jdbc, "task_v7_approved"));
        assertEquals("IN_REVIEW", taskStatus(jdbc, "task_v7_approved"));
        assertNull(resolvedAt(jdbc, "task_v7_approved"));
        assertEquals("REJECT", decision(jdbc, "task_v7_rejected"));
        assertEquals("CLOSED", taskStatus(jdbc, "task_v7_rejected"));
        assertEquals("APPROVE", decision(jdbc, "task_v7_published"));
        assertEquals("CLOSED", taskStatus(jdbc, "task_v7_published"));

        LabelReviewService service = new LabelReviewService(
                new AuthorizationService(),
                new JdbcLabelReviewCommandRepository(jdbc),
                new LabelTransitionPolicy(),
                new MakerCheckerPolicy());
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        AuthenticatedActor publisher = new AuthenticatedActor(
                "user_publisher", "publication.manager", "Demo Publisher",
                Set.of(), Set.of("LABEL.PUBLISH"));
        transaction.executeWithoutResult(status -> service.publishReviewTask(
                "task_v7_approved", approvedLabel, publisher));

        assertEquals("PUBLISHED", labelStatus(jdbc, approvedLabel));
        assertEquals(approvedLabel, jdbc.queryForObject(
                "SELECT current_published_label_version_id FROM product WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)",
                String.class, approvedLabel));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM publication_record WHERE label_version_id = ?",
                Integer.class, approvedLabel));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type = 'LABEL_PUBLISHED' AND entity_id = ?",
                Integer.class, approvedLabel));
        assertEquals("CLOSED", taskStatus(jdbc, "task_v7_approved"));
    }

    private String createTaskFixture(
            JdbcTemplate jdbc,
            String key,
            String lifecycle,
            String decision,
            String taskStatus,
            boolean usePrimaryProduct
    ) {
        String productId = usePrimaryProduct
                ? "prod_usda_1106285"
                : "prod_usda_1106963";
        String currentLabel = currentPublishedLabel(jdbc, productId);
        String labelId = "label_" + key;
        jdbc.update("""
                INSERT INTO label_version (
                    label_version_id, product_id, formula_version_id,
                    rule_set_version_id, jurisdiction_code, version_number,
                    raw_ingredient_text, lifecycle_status, is_current_published,
                    created_by_user_id, created_at, data_provenance_id
                )
                SELECT ?, p.product_id, p.current_formula_version_id,
                       lv.rule_set_version_id, lv.jurisdiction_code,
                       (SELECT COALESCE(MAX(v.version_number), 0) + 1
                        FROM label_version v WHERE v.product_id = p.product_id
                          AND v.jurisdiction_code = lv.jurisdiction_code),
                       lv.raw_ingredient_text, ?, 'N', 'user_label_officer',
                       NOW(), lv.data_provenance_id
                FROM product p
                JOIN label_version lv ON lv.label_version_id = ?
                WHERE p.product_id = ?
                """, labelId, lifecycle, currentLabel, productId);
        if ("APPROVED".equals(lifecycle)) {
            jdbc.update("""
                    INSERT INTO validation_run (
                        validation_run_id, label_version_id, rule_set_version_id,
                        status, ran_by_user_id, ran_at, summary, data_provenance_id
                    )
                    SELECT ?, label_version_id, rule_set_version_id, 'PASSED',
                           'user_label_officer', NOW(), 'V6 migration test passed validation',
                           data_provenance_id
                    FROM label_version WHERE label_version_id = ?
                    """, "validation_" + key, labelId);
        }
        createTaskFixtureForExistingLabel(jdbc, key, labelId, decision, taskStatus);
        return labelId;
    }

    private void createTaskFixtureForExistingLabel(
            JdbcTemplate jdbc,
            String key,
            String labelId,
            String decision,
            String taskStatus
    ) {
        String changeId = "change_" + key;
        String runId = "run_" + key;
        String findingId = "finding_" + key;
        String productId = jdbc.queryForObject(
                "SELECT product_id FROM label_version WHERE label_version_id = ?",
                String.class, labelId);
        String currentLabel = currentPublishedLabel(jdbc, productId);
        jdbc.update("""
                INSERT INTO change_request (
                    change_request_id, change_request_code, change_type, status,
                    requested_at, requested_by_user_id, description,
                    from_formula_version_id, to_formula_version_id, data_provenance_id
                )
                SELECT ?, ?, 'FORMULA', 'ANALYZED', NOW(), 'user_label_officer',
                       'V6 migration test', formula_version_id, formula_version_id,
                       data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, changeId, "code_" + key, labelId);
        jdbc.update("""
                INSERT INTO impact_analysis_run (
                    impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                    rule_set_version_id, status, started_at, completed_at,
                    executed_by_user_id, data_provenance_id
                )
                SELECT ?, ?, ?, ?, rule_set_version_id, 'COMPLETED', NOW(), NOW(),
                       'user_label_officer', data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, runId, "run-code-" + key, changeId,
                "impact-analysis:" + changeId, labelId);
        jdbc.update("""
                INSERT INTO impact_finding (
                    impact_finding_id, impact_analysis_run_id, product_id,
                    current_formula_version_id, proposed_formula_version_id,
                    current_label_version_id, classification, missing_allergen_codes,
                    explanation, data_provenance_id
                )
                SELECT ?, ?, lv.product_id, lv.formula_version_id,
                       lv.formula_version_id, ?, 'REVIEW_REQUIRED', JSON_ARRAY(),
                       'V6 migration test', lv.data_provenance_id
                FROM label_version lv WHERE lv.label_version_id = ?
                """, findingId, runId, currentLabel, labelId);
        jdbc.update("""
                INSERT INTO review_task (
                    review_task_id, impact_finding_id, product_id,
                    current_label_version_id, draft_label_version_id, status,
                    assigned_to_user_id, created_by_user_id, created_at,
                    data_provenance_id
                )
                SELECT ?, ?, product_id, ?, ?, ?, 'user_approver',
                       'user_label_officer', NOW(), data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, "task_" + key, findingId, currentLabel, labelId,
                taskStatus, labelId);
        jdbc.update("""
                INSERT INTO approval_record (
                    approval_record_id, label_version_id, review_task_id,
                    decision, decided_by_user_id, decided_at, comments,
                    data_provenance_id
                )
                SELECT ?, ?, ?, ?, 'user_approver', NOW(), 'V6 history',
                       data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, "approval_" + key, labelId, "task_" + key,
                decision, labelId);
        if ("v7_published".equals(key)) {
            jdbc.update("""
                    INSERT INTO publication_record (
                        publication_record_id, label_version_id, published_by_user_id,
                        published_at, publication_channel, data_provenance_id
                    )
                    SELECT 'publication_v7_existing', ?, 'user_publisher', NOW(),
                           'DEMO_RELEASE', data_provenance_id
                    FROM label_version WHERE label_version_id = ?
                    """, labelId, labelId);
        }
    }

    private String currentPublishedLabel(JdbcTemplate jdbc, String productId) {
        return jdbc.queryForObject("""
                SELECT label_version_id FROM label_version
                WHERE product_id = ? AND lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y' LIMIT 1
                """, String.class, productId);
    }

    private String decision(JdbcTemplate jdbc, String taskId) {
        return jdbc.queryForObject(
                "SELECT decision FROM review_task WHERE review_task_id = ?",
                String.class, taskId);
    }

    private String taskStatus(JdbcTemplate jdbc, String taskId) {
        return jdbc.queryForObject(
                "SELECT status FROM review_task WHERE review_task_id = ?",
                String.class, taskId);
    }

    private String labelStatus(JdbcTemplate jdbc, String labelId) {
        return jdbc.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class, labelId);
    }

    private java.sql.Timestamp resolvedAt(JdbcTemplate jdbc, String taskId) {
        return jdbc.queryForObject(
                "SELECT resolved_at FROM review_task WHERE review_task_id = ?",
                java.sql.Timestamp.class, taskId);
    }
}
