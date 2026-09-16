package com.spectrace.workflow;

import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkflowIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LabelWorkflowRepository workflowRepository;

    private static final String LABEL_ID = "label_m4_workflow_test";
    private static final String REVIEW_TASK_ID = "review_task_scrum37";
    private static final String IMPACT_FINDING_ID = "impact_finding_scrum37";
    private static final String IMPACT_RUN_ID = "impact_run_scrum37";
    private static final String CHANGE_REQUEST_ID = "change_request_scrum37";

    @BeforeEach
    void prepareFixture() {
        jdbcTemplate.update(
                "DELETE FROM approval_record WHERE label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM review_task WHERE review_task_id = ?",
                REVIEW_TASK_ID
        );

        jdbcTemplate.update(
                "DELETE FROM impact_finding WHERE impact_finding_id = ?",
                IMPACT_FINDING_ID
        );

        jdbcTemplate.update(
                "DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?",
                IMPACT_RUN_ID
        );

        jdbcTemplate.update(
                "DELETE FROM change_request WHERE change_request_id = ?",
                CHANGE_REQUEST_ID
        );

        jdbcTemplate.update(
                "DELETE FROM publication_record WHERE label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM validation_result WHERE validation_run_id IN " +
                        "(SELECT validation_run_id FROM validation_run WHERE label_version_id = ?)",
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM validation_run WHERE label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                "UPDATE product SET current_published_label_version_id = NULL " +
                        "WHERE current_published_label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM label_version WHERE label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO label_version (
                    label_version_id,
                    product_id,
                    formula_version_id,
                    rule_set_version_id,
                    jurisdiction_code,
                    version_number,
                    raw_ingredient_text,
                    lifecycle_status,
                    is_current_published,
                    created_by_user_id,
                    created_at,
                    data_provenance_id
                )
                SELECT
                    ?,
                    product_id,
                    formula_version_id,
                    rule_set_version_id,
                    jurisdiction_code,
                    999,
                    raw_ingredient_text,
                    'DRAFT',
                    'N',
                    'user_label_officer',
                    NOW(),
                    data_provenance_id
                FROM label_version
                WHERE lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y'
                LIMIT 1
                """,
                LABEL_ID
        );
    }

    @Test
    void rejectsSubmitWithoutPassedValidation() {
        assertThrows(
                DataAccessException.class,
                () -> workflowRepository.submitForReview(
                        LABEL_ID,
                        "user_label_officer"
                )
        );

        assertStatus("DRAFT");
    }

    @Test
    void rejectsDecisionWithoutPendingReviewTask() {
        assertThrows(
                DataAccessException.class,
                () -> workflowRepository.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "user_approver",
                        "Invalid transition test"
                )
        );

        assertStatus("DRAFT");
    }

    @Test
    void allowsDraftToPendingReviewAfterPassedValidation() {
        createPassedValidation();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsSecondSubmitAfterPendingReview() {
        createPassedValidation();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                DataAccessException.class,
                () -> workflowRepository.submitForReview(
                        LABEL_ID,
                        "user_label_officer"
                )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsApprovalAfterAlreadyPendingReviewWithoutReviewTask() {
        createPassedValidation();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                DataAccessException.class,
                () -> workflowRepository.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "user_approver",
                        "No review task exists"
                )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void allowsPendingReviewToApproved() {
        createPassedValidation();
        createReviewFixture();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        assertStatus("PENDING_REVIEW");

        workflowRepository.recordDecision(
                LABEL_ID,
                "APPROVE",
                "user_approver",
                "SCRUM-37 approval integration test"
        );

        assertStatus("APPROVED");
    }

    @Test
    void allowsRequestChangesToReturnPendingReviewToDraft() {
        createPassedValidation();
        createReviewFixture();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        assertStatus("PENDING_REVIEW");

        workflowRepository.recordDecision(
                LABEL_ID,
                "REQUEST_CHANGES",
                "user_approver",
                "SCRUM-37 request changes integration test"
        );

        assertStatus("DRAFT");
    }

    @Test
    void publishesApprovedLabelAndSupersedesPreviousPublishedLabel() {
        createPassedValidation();
        createReviewFixture();

        workflowRepository.submitForReview(
                LABEL_ID,
                "user_label_officer"
        );

        workflowRepository.recordDecision(
                LABEL_ID,
                "APPROVE",
                "user_approver",
                "SCRUM-37 publication integration test"
        );

        assertStatus("APPROVED");

        String oldPublishedLabelId = jdbcTemplate.queryForObject(
                """
                SELECT old_label.label_version_id
                FROM label_version old_label
                JOIN label_version test_label
                  ON test_label.label_version_id = ?
                 AND old_label.product_id = test_label.product_id
                 AND old_label.jurisdiction_code = test_label.jurisdiction_code
                WHERE old_label.lifecycle_status = 'PUBLISHED'
                  AND old_label.is_current_published = 'Y'
                  AND old_label.label_version_id <> ?
                LIMIT 1
                """,
                String.class,
                LABEL_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                "CALL sp_publish_label(?, ?)",
                LABEL_ID,
                "user_publisher"
        );

        assertStatus("PUBLISHED");

        String currentFlag = jdbcTemplate.queryForObject(
                "SELECT is_current_published FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );

        assertEquals("Y", currentFlag);

        String oldStatus = jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class,
                oldPublishedLabelId
        );

        String oldCurrentFlag = jdbcTemplate.queryForObject(
                "SELECT is_current_published FROM label_version WHERE label_version_id = ?",
                String.class,
                oldPublishedLabelId
        );

        assertEquals("SUPERSEDED", oldStatus);
        assertEquals("N", oldCurrentFlag);
    }

    private void createReviewFixture() {
        jdbcTemplate.update(
                """
                INSERT INTO change_request (
                    change_request_id,
                    change_request_code,
                    change_type,
                    status,
                    requested_at,
                    requested_by_user_id,
                    description,
                    from_formula_version_id,
                    to_formula_version_id,
                    data_provenance_id
                )
                SELECT
                    ?,
                    'CR-SCRUM-37-TEST',
                    'FORMULA',
                    'ANALYZED',
                    NOW(),
                    'user_label_officer',
                    'SCRUM-37 workflow integration fixture',
                    formula_version_id,
                    formula_version_id,
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                CHANGE_REQUEST_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO impact_analysis_run (
                    impact_analysis_run_id,
                    run_code,
                    change_request_id,
                    rule_set_version_id,
                    status,
                    started_at,
                    completed_at,
                    executed_by_user_id,
                    data_provenance_id
                )
                SELECT
                    ?,
                    'IMPACT-SCRUM-37-TEST',
                    ?,
                    rule_set_version_id,
                    'COMPLETED',
                    NOW(),
                    NOW(),
                    'user_label_officer',
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                IMPACT_RUN_ID,
                CHANGE_REQUEST_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO impact_finding (
                    impact_finding_id,
                    impact_analysis_run_id,
                    product_id,
                    current_formula_version_id,
                    proposed_formula_version_id,
                    current_label_version_id,
                    classification,
                    missing_allergen_codes,
                    explanation,
                    data_provenance_id
                )
                SELECT
                    ?,
                    ?,
                    test_label.product_id,
                    test_label.formula_version_id,
                    test_label.formula_version_id,
                    current_label.label_version_id,
                    'REVIEW_REQUIRED',
                    JSON_ARRAY(),
                    'SCRUM-37 workflow integration fixture',
                    test_label.data_provenance_id
                FROM label_version test_label
                JOIN label_version current_label
                  ON current_label.product_id = test_label.product_id
                 AND current_label.jurisdiction_code = test_label.jurisdiction_code
                 AND current_label.lifecycle_status = 'PUBLISHED'
                 AND current_label.is_current_published = 'Y'
                WHERE test_label.label_version_id = ?
                LIMIT 1
                """,
                IMPACT_FINDING_ID,
                IMPACT_RUN_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                INSERT INTO review_task (
                    review_task_id,
                    impact_finding_id,
                    product_id,
                    current_label_version_id,
                    draft_label_version_id,
                    status,
                    assigned_to_user_id,
                    created_by_user_id,
                    created_at,
                    data_provenance_id
                )
                SELECT
                    ?,
                    impact_finding_id,
                    product_id,
                    current_label_version_id,
                    ?,
                    'OPEN',
                    'user_approver',
                    'user_label_officer',
                    NOW(),
                    data_provenance_id
                FROM impact_finding
                WHERE impact_finding_id = ?
                """,
                REVIEW_TASK_ID,
                LABEL_ID,
                IMPACT_FINDING_ID
        );
    }

    private void createPassedValidation() {
        jdbcTemplate.update(
                """
                INSERT INTO validation_run (
                    validation_run_id,
                    label_version_id,
                    rule_set_version_id,
                    status,
                    ran_by_user_id,
                    ran_at,
                    summary,
                    data_provenance_id
                )
                SELECT
                    ?,
                    label_version_id,
                    rule_set_version_id,
                    'PASSED',
                    'user_label_officer',
                    NOW(),
                    'SCRUM-37 lifecycle transition test',
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                "validation_run_scrum37",
                LABEL_ID
        );
    }

    private void assertStatus(String expectedStatus) {
        String actualStatus = jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );

        assertEquals(expectedStatus, actualStatus);
    }
}