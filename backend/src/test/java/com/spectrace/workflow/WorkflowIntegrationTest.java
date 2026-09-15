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

    @BeforeEach
    void prepareFixture() {
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