package com.spectrace.workflow;

import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.LabelReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FreshImpactTaskLabelDraftHandoffMySqlTest
        extends MySqlIntegrationTestSupport {

    private static final String TASK_ID = "review_task_scrum82_fresh_m1";
    private static final String FINDING_ID = "impact_finding_scrum82_fresh_m1";
    private static final String RUN_ID = "impact_run_scrum82_fresh_m1";
    private static final String CHANGE_ID = "change_request_scrum82_fresh_m1";
    private static final String PRODUCT_ID = "prod_usda_1106285";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LabelReviewService reviewService;

    @LocalServerPort
    private int serverPort;

    @Test
    void submissionBindsTaskLinkedOnlyByDraftReferenceAndAllowsItsRealApproval()
            throws Exception {
        cleanUp();
        createFreshImpactTask();

        assertEquals("OPEN", taskStatus());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id = ? AND draft_label_version_id IS NULL AND target_label_version_id IS NULL",
                Integer.class, TASK_ID));

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + serverPort + "/api/labels/drafts"))
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", "dev-external-label-officer")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"productId":"%s","jurisdictionCode":"US"}
                        """.formatted(PRODUCT_ID)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        Matcher labelIdMatch = Pattern.compile(
                "\\\"labelVersionId\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        assertTrue(labelIdMatch.find(), response.body());
        String labelId = labelIdMatch.group(1);

        assertEquals(labelId, jdbc.queryForObject(
                "SELECT draft_label_version_id FROM review_task WHERE review_task_id = ?",
                String.class, TASK_ID));
        assertEquals(labelId, jdbc.queryForObject(
                "SELECT target_label_version_id FROM review_task WHERE review_task_id = ?",
                String.class, TASK_ID));

        jdbc.update("""
                INSERT INTO validation_run (
                    validation_run_id, label_version_id, rule_set_version_id,
                    status, ran_by_user_id, ran_at, summary, data_provenance_id
                )
                SELECT ?, label_version_id, rule_set_version_id, 'PASSED',
                       'user_label_officer', NOW(), 'Fresh M1 handoff', data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, "validation_scrum82_" + labelId, labelId);

        reviewService.submitForReview(labelId, actor("user_label_officer", "LABEL.SUBMIT_REVIEW"));
        assertEquals("PENDING_REVIEW", labelStatus(labelId));
        assertEquals("IN_REVIEW", taskStatus());
        assertEquals(labelId, jdbc.queryForObject(
                "SELECT draft_label_version_id FROM review_task WHERE review_task_id = ?",
                String.class, TASK_ID));
        assertEquals(labelId, jdbc.queryForObject(
                "SELECT target_label_version_id FROM review_task WHERE review_task_id = ?",
                String.class, TASK_ID));

        reviewService.recordDecision(
                labelId, "APPROVE", "Fresh M1 task handoff",
                actor("user_approver", "LABEL.APPROVE"));
        assertEquals("APPROVED", labelStatus(labelId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE review_task_id = ? AND label_version_id = ? AND decision = 'APPROVE'",
                Integer.class, TASK_ID, labelId));
    }

    @AfterEach
    void cleanUp() {
        String labelId = jdbc.query(
                "SELECT draft_label_version_id FROM review_task WHERE review_task_id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                TASK_ID);
        if (labelId != null) {
            jdbc.update("DELETE FROM audit_event WHERE entity_id = ?", labelId);
            jdbc.update("DELETE FROM approval_record WHERE review_task_id = ?", TASK_ID);
            jdbc.update("DELETE FROM validation_result WHERE validation_run_id IN (SELECT validation_run_id FROM validation_run WHERE label_version_id = ?)", labelId);
            jdbc.update("DELETE FROM validation_run WHERE label_version_id = ?", labelId);
        }
        jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", TASK_ID);
        jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", FINDING_ID);
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", RUN_ID);
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", CHANGE_ID);
        if (labelId != null) {
            jdbc.update("DELETE FROM label_version WHERE label_version_id = ?", labelId);
        }
    }

    private void createFreshImpactTask() {
        jdbc.update("""
                INSERT INTO change_request (
                    change_request_id, change_request_code, change_type, status,
                    requested_at, requested_by_user_id, description,
                    from_formula_version_id, to_formula_version_id, data_provenance_id
                )
                SELECT ?, ?, 'FORMULA', 'ANALYZED', NOW(), 'user_label_officer',
                       'Fresh M1 review task handoff', current_formula_version_id,
                       current_formula_version_id, data_provenance_id
                FROM product WHERE product_id = ?
                """, CHANGE_ID, "code_" + CHANGE_ID, PRODUCT_ID);
        jdbc.update("""
                INSERT INTO impact_analysis_run (
                    impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                    rule_set_version_id, status, started_at, completed_at,
                    executed_by_user_id, data_provenance_id
                )
                SELECT ?, ?, ?, ?, lv.rule_set_version_id, 'COMPLETED', NOW(), NOW(),
                       'user_label_officer', p.data_provenance_id
                FROM product p
                JOIN label_version lv
                  ON lv.label_version_id = p.current_published_label_version_id
                WHERE p.product_id = ?
                """, RUN_ID, "run-code-" + RUN_ID, CHANGE_ID,
                "impact-analysis:" + CHANGE_ID, PRODUCT_ID);
        jdbc.update("""
                INSERT INTO impact_finding (
                    impact_finding_id, impact_analysis_run_id, product_id,
                    current_formula_version_id, proposed_formula_version_id,
                    current_label_version_id, classification, missing_allergen_codes,
                    explanation, data_provenance_id
                )
                SELECT ?, ?, p.product_id, p.current_formula_version_id,
                       p.current_formula_version_id,
                       p.current_published_label_version_id, 'REVIEW_REQUIRED',
                       JSON_ARRAY(), 'Fresh M1 review task handoff', p.data_provenance_id
                FROM product p WHERE p.product_id = ?
                """, FINDING_ID, RUN_ID, PRODUCT_ID);
        jdbc.update("""
                INSERT INTO review_task (
                    review_task_id, impact_finding_id, product_id,
                    current_label_version_id, draft_label_version_id,
                    target_label_version_id, status, assigned_to_user_id,
                    created_by_user_id, created_at, data_provenance_id
                )
                SELECT ?, ?, p.product_id, p.current_published_label_version_id,
                       NULL, NULL, 'OPEN', 'user_approver', 'user_label_officer',
                       NOW(), p.data_provenance_id
                FROM product p WHERE p.product_id = ?
                """, TASK_ID, FINDING_ID, PRODUCT_ID);
    }

    private AuthenticatedActor actor(String userId, String permission) {
        return new AuthenticatedActor(
                userId, userId, userId, Set.of(), Set.of(permission));
    }

    private String taskStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM review_task WHERE review_task_id = ?",
                String.class, TASK_ID);
    }

    private String labelStatus(String labelId) {
        return jdbc.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class, labelId);
    }
}
