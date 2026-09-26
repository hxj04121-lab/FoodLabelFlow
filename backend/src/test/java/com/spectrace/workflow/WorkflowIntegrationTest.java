package com.spectrace.workflow;

import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.LabelPublicationService;
import com.spectrace.workflow.application.LabelReviewService;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkflowIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private LabelReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LabelPublicationService publicationService;

    @Autowired
    private LabelWorkflowRepository workflowRepository;

    private static final String LABEL_ID = "label_m4_workflow_test";
    private static final String REVIEW_TASK_ID = "review_task_scrum37";
    private static final String IMPACT_FINDING_ID = "impact_finding_scrum37";
    private static final String IMPACT_RUN_ID = "impact_run_scrum37";
    private static final String CHANGE_REQUEST_ID = "change_request_scrum37";

    @BeforeEach
    void prepareFixture() {
        cleanUpTestData();

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

    @AfterEach
    void restoreFixture() {
        restoreSupersededBaseline();
        cleanUpTestData();
    }

    @Test
    void identifiesLatestDraftAsCurrent() {
        LabelWorkflowRepository.LabelWorkflowVersion version =
                workflowRepository.findVersion(LABEL_ID)
                        .orElseThrow();

        assertEquals("DRAFT", version.lifecycleStatus());
        assertTrue(version.current());
    }

    @Test
    void identifiesOlderDraftAsStaleWhenNewerVersionExists() {
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
                    'label_scrum39_newer',
                    product_id,
                    formula_version_id,
                    rule_set_version_id,
                    jurisdiction_code,
                    1000,
                    raw_ingredient_text,
                    'DRAFT',
                    'N',
                    'user_label_officer',
                    NOW(),
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                LABEL_ID
        );

        try {
            LabelWorkflowRepository.LabelWorkflowVersion version =
                    workflowRepository.findVersion(LABEL_ID)
                            .orElseThrow();

            assertEquals("DRAFT", version.lifecycleStatus());
            assertFalse(version.current());
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM label_version WHERE label_version_id = 'label_scrum39_newer'"
            );
        }
    }

    @Test
    void identifiesSupersededLabelAsHistorical() {
        jdbcTemplate.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'SUPERSEDED',
                    is_current_published = 'N'
                WHERE label_version_id = ?
                """,
                LABEL_ID
        );

        LabelWorkflowRepository.LabelWorkflowVersion version =
                workflowRepository.findVersion(LABEL_ID)
                        .orElseThrow();

        assertEquals("SUPERSEDED", version.lifecycleStatus());
        assertFalse(version.current());
    }
    @Test
    void rejectsSubmitWithoutPassedValidation() {
        assertThrows(
                IllegalStateException.class,
                () -> reviewService.submitForReview(
                        LABEL_ID,
                        new AuthenticatedActor(
                                "user_label_officer",
                                "user_label_officer",
                                "Label Officer",
                                Set.of(),
                                Set.of("LABEL.SUBMIT_REVIEW")
                        )
                )
        );

        assertStatus("DRAFT");
    }

    @Test
    void rejectsDecisionWithoutPendingReviewTask() {
        assertThrows(
                IllegalStateException.class,
                () -> reviewService.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "Invalid transition test",
                        new AuthenticatedActor(
                                "user_approver",
                                "user_approver",
                                "Approver",
                                Set.of(),
                                Set.of("LABEL.APPROVE")
                        )
                )
        );

        assertStatus("DRAFT");
    }

    @Test
    void allowsDraftToPendingReviewAfterPassedValidation() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsSecondSubmitAfterPendingReview() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.submitForReview(
                        LABEL_ID,
                        new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
                )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsApprovalAfterAlreadyPendingReviewWithoutReviewTask() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "No review task exists",
                        new AuthenticatedActor(
                                "user_approver",
                                "user_approver",
                                "Approver",
                                Set.of(),
                                Set.of("LABEL.APPROVE")
                        )
                )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void allowsPendingReviewToApproved() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "SCRUM-50 Java approval integration test",
                new AuthenticatedActor(
                        "user_approver",
                        "user_approver",
                        "Approver",
                        Set.of(),
                        Set.of("LABEL.APPROVE")
                )
        );

        assertStatus("APPROVED");
    }

    @Test
    void allowsRequestChangesToReturnPendingReviewToDraft() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "REQUEST_CHANGES",
                "SCRUM-50 Java request changes integration test",
                new AuthenticatedActor(
                        "user_approver",
                        "user_approver",
                        "Approver",
                        Set.of(),
                        Set.of("LABEL.REQUEST_CHANGES")
                )
        );

        assertStatus("DRAFT");
    }

    @Test
    void allowsRejectToMovePendingReviewToRejected() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        assertStatus("PENDING_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "REJECT",
                "SCRUM-50 Java rejection integration test",
                new AuthenticatedActor(
                        "user_approver",
                        "user_approver",
                        "Approver",
                        Set.of(),
                        Set.of("LABEL.REJECT")
                )
        );

        assertStatus("REJECTED");

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM approval_record
                        WHERE label_version_id = ?
                          AND decision = 'REJECT'
                        """,
                        Integer.class,
                        LABEL_ID
                )
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM audit_event
                        WHERE entity_type = 'LABEL_VERSION'
                          AND entity_id = ?
                          AND event_type = 'LABEL_DECISION_RECORDED'
                        """,
                        Integer.class,
                        LABEL_ID
                )
        );
    }
    @Test
    void publishesApprovedLabelAndSupersedesPreviousPublishedLabel() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "SCRUM-50 publication integration test",
                new AuthenticatedActor(
                        "user_approver",
                        "user_approver",
                        "Approver",
                        Set.of(),
                        Set.of("LABEL.APPROVE")
                )
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

AuthenticatedActor publisher =
        new AuthenticatedActor(
                "user_publisher",
                "user_publisher",
                "Publisher",
                Set.of(),
                Set.of("LABEL.PUBLISH")
        );

publicationService.publishLabel(
        LABEL_ID,
        publisher
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

    @Test
    void rejectsJavaPublicationWithoutApproveRecord() {
        jdbcTemplate.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'APPROVED'
                WHERE label_version_id = ?
                """,
                LABEL_ID
        );

        AuthenticatedActor publisher =
                new AuthenticatedActor(
                        "user_publisher",
                        "user_publisher",
                        "Publisher",
                        Set.of(),
                        Set.of("LABEL.PUBLISH")
                );

        assertThrows(
                IllegalStateException.class,
                () -> publicationService.publishLabel(
                        LABEL_ID,
                        publisher
                )
        );

        assertStatus("APPROVED");

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM publication_record
                        WHERE label_version_id = ?
                        """,
                        Integer.class,
                        LABEL_ID
                )
        );

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM audit_event
                        WHERE entity_type = 'LABEL_VERSION'
                          AND entity_id = ?
                          AND event_type = 'LABEL_PUBLISHED'
                        """,
                        Integer.class,
                        LABEL_ID
                )
        );
    }

    @Test
    void allowsOnlyOneConcurrentJavaPublication() throws Exception {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                new AuthenticatedActor(
                        "user_label_officer",
                        "user_label_officer",
                        "Label Officer",
                        Set.of(),
                        Set.of("LABEL.SUBMIT_REVIEW")
                )
        );

        reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "SCRUM-50 concurrent publication test",
                new AuthenticatedActor(
                        "user_approver",
                        "user_approver",
                        "Approver",
                        Set.of(),
                        Set.of("LABEL.APPROVE")
                )
        );

        assertStatus("APPROVED");

        AuthenticatedActor publisher =
                new AuthenticatedActor(
                        "user_publisher",
                        "user_publisher",
                        "Publisher",
                        Set.of(),
                        Set.of("LABEL.PUBLISH")
                );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Future<?> first = executor.submit(() -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                publicationService.publishLabel(
                        LABEL_ID,
                        publisher
                );
                successes.incrementAndGet();
            } catch (Exception error) {
                failures.incrementAndGet();
            }
        });

        Future<?> second = executor.submit(() -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                publicationService.publishLabel(
                        LABEL_ID,
                        publisher
                );
                successes.incrementAndGet();
            } catch (Exception error) {
                failures.incrementAndGet();
            }
        });

        start.countDown();

        try {
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);

            assertEquals(1, successes.get());
            assertEquals(1, failures.get());

            assertStatus("PUBLISHED");

            assertEquals(
                    LABEL_ID,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT current_published_label_version_id
                            FROM product
                            WHERE product_id = (
                                SELECT product_id
                                FROM label_version
                                WHERE label_version_id = ?
                            )
                            """,
                            String.class,
                            LABEL_ID
                    )
            );

            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM publication_record
                            WHERE label_version_id = ?
                            """,
                            Integer.class,
                            LABEL_ID
                    )
            );

            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM audit_event
                            WHERE entity_type = 'LABEL_VERSION'
                              AND entity_id = ?
                              AND event_type = 'LABEL_PUBLISHED'
                            """,
                            Integer.class,
                            LABEL_ID
                    )
            );

            assertEquals(
                    LABEL_ID,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT target_label_version_id
                            FROM review_task
                            WHERE review_task_id = ?
                            """,
                            String.class,
                            REVIEW_TASK_ID
                    )
            );

            assertEquals(
                    "CLOSED",
                    jdbcTemplate.queryForObject(
                            """
                            SELECT status
                            FROM review_task
                            WHERE review_task_id = ?
                            """,
                            String.class,
                            REVIEW_TASK_ID
                    )
            );
        } finally {
            executor.shutdownNow();
        }
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

        private void restoreSupersededBaseline() {
        Integer testLabelCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM label_version WHERE label_version_id = ?",
                Integer.class,
                LABEL_ID
        );

        if (testLabelCount == null || testLabelCount == 0) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'APPROVED',
                    is_current_published = 'N'
                WHERE label_version_id = ?
                  AND lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y'
                """,
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                UPDATE label_version baseline
                JOIN label_version test_label
                  ON test_label.label_version_id = ?
                 AND baseline.product_id = test_label.product_id
                 AND baseline.jurisdiction_code = test_label.jurisdiction_code
                SET baseline.lifecycle_status = 'PUBLISHED',
                    baseline.is_current_published = 'Y'
                WHERE baseline.label_version_id <> ?
                  AND baseline.lifecycle_status = 'SUPERSEDED'
                  AND baseline.is_current_published = 'N'
                """,
                LABEL_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                """
                UPDATE product p
                JOIN label_version baseline
                  ON baseline.product_id = p.product_id
                 AND baseline.lifecycle_status = 'PUBLISHED'
                 AND baseline.is_current_published = 'Y'
                JOIN label_version test_label
                  ON test_label.label_version_id = ?
                 AND test_label.product_id = p.product_id
                 AND test_label.jurisdiction_code = baseline.jurisdiction_code
                SET p.current_published_label_version_id = baseline.label_version_id
                """,
                LABEL_ID
        );
    }

    private void cleanUpTestData() {
        jdbcTemplate.update(
                """
                DELETE FROM audit_event
                WHERE entity_type = 'LABEL_VERSION'
                  AND entity_id = ?
                """,
                LABEL_ID
        );
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