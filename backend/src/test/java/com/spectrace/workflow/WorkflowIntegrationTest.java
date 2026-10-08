package com.spectrace.workflow;

import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkflowIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private LabelReviewService reviewService;

    @Autowired
    private com.spectrace.label.application.LabelDraftService labelDraftService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LabelWorkflowRepository workflowRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private LabelReviewCommandRepository commandRepository;

    private static final String LABEL_ID = "label_m4_workflow_test";
    private static final String REVIEW_TASK_ID = "review_task_scrum37";
    private static final String IMPACT_FINDING_ID = "impact_finding_scrum37";
    private static final String IMPACT_RUN_ID = "impact_run_scrum37";
    private static final String CHANGE_REQUEST_ID = "change_request_scrum37";
    private static final String STALE_FORMULA_ID = "formula_scrum81_stale_decision";

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
                reviewSubmitter()
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
                reviewApprover("LABEL.APPROVE")
        )
        );

        assertStatus("DRAFT");
    }

    @Test
    void allowsDraftToPendingReviewAfterPassedValidation() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsSecondSubmitAfterPendingReview() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        )
        );

        assertStatus("PENDING_REVIEW");
    }

    @Test
    void rejectsApprovalAfterAlreadyPendingReviewWithoutReviewTask() {
        createPassedValidation();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "No review task exists",
                reviewApprover("LABEL.APPROVE")
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
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "SCRUM-37 approval integration test",
                reviewApprover("LABEL.APPROVE")
        );

        assertStatus("APPROVED");
        assertReviewTaskStatus("IN_REVIEW");
    }

    @Test
    void allowsRequestChangesToReturnPendingReviewToDraft() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "REQUEST_CHANGES",
                "SCRUM-37 request changes integration test",
                reviewApprover("LABEL.REQUEST_CHANGES")
        );

        assertStatus("DRAFT");
        assertReviewTaskStatus("OPEN");
    }

    @Test
    void closesReviewTaskWhenLabelIsRejected() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        assertStatus("PENDING_REVIEW");
        assertReviewTaskStatus("IN_REVIEW");

        reviewService.recordDecision(
                LABEL_ID,
                "REJECT",
                "SCRUM-81 reject integration test",
                reviewApprover("LABEL.REJECT")
        );

        assertStatus("REJECTED");
        assertReviewTaskStatus("CLOSED");
    }

    @Test
    void rejectsDecisionWhenFormulaWasChangedWithoutCreatingNewLabel() {
        createPassedValidation();
        createReviewFixture();
        reviewService.submitForReview(LABEL_ID, reviewSubmitter());
        assertStatus("PENDING_REVIEW");
        assertReviewTaskStatus("IN_REVIEW");

        String productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );
        String originalFormulaId = jdbcTemplate.queryForObject(
                "SELECT formula_version_id FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );
        jdbcTemplate.update("""
                INSERT INTO formula_version (
                    formula_version_id, product_id, version_number,
                    lifecycle_status, is_current_released, created_by_user_id,
                    released_by_user_id, released_at, data_provenance_id
                )
                SELECT ?, product_id, version_number + 1000, 'RELEASED', 'N',
                       created_by_user_id, created_by_user_id, NOW(), data_provenance_id
                FROM formula_version WHERE formula_version_id = ?
                """,
                STALE_FORMULA_ID,
                originalFormulaId
        );

        try {
            jdbcTemplate.update(
                    "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                    STALE_FORMULA_ID,
                    productId
            );
            assertThrows(LabelVersionConflictException.class,
                    () -> reviewService.recordDecision(
                            LABEL_ID,
                            "APPROVE",
                            "SCRUM-81 stale formula regression",
                            reviewApprover("LABEL.APPROVE")
                    ));
            assertDecisionUnchanged();
        } finally {
            jdbcTemplate.update(
                    "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                    originalFormulaId,
                    productId
            );
            jdbcTemplate.update(
                    "DELETE FROM formula_version WHERE formula_version_id = ?",
                    STALE_FORMULA_ID
            );
        }
    }

    @Test
    void serializesDecisionAgainstConcurrentFormulaSwitch() throws Exception {
        createPassedValidation();
        createReviewFixture();
        reviewService.submitForReview(LABEL_ID, reviewSubmitter());
        String productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );
        String originalFormulaId = jdbcTemplate.queryForObject(
                "SELECT formula_version_id FROM label_version WHERE label_version_id = ?",
                String.class,
                LABEL_ID
        );
        jdbcTemplate.update("""
                INSERT INTO formula_version (
                    formula_version_id, product_id, version_number,
                    lifecycle_status, is_current_released, created_by_user_id,
                    released_by_user_id, released_at, data_provenance_id
                )
                SELECT ?, product_id, version_number + 1001, 'RELEASED', 'N',
                       created_by_user_id, created_by_user_id, NOW(), data_provenance_id
                FROM formula_version WHERE formula_version_id = ?
                """,
                STALE_FORMULA_ID,
                originalFormulaId
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch decisionStarted = new CountDownLatch(1);
        Future<?> decision;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT product_id FROM product WHERE product_id = ? FOR UPDATE"
            )) {
                lock.setString(1, productId);
                lock.executeQuery();
            }
            decision = executor.submit(() -> {
                decisionStarted.countDown();
                reviewService.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "SCRUM-81 concurrent formula switch",
                        reviewApprover("LABEL.APPROVE")
                );
            });
            assertTrue(decisionStarted.await(10, TimeUnit.SECONDS));
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?"
            )) {
                update.setString(1, STALE_FORMULA_ID);
                update.setString(2, productId);
                assertEquals(1, update.executeUpdate());
            }
            connection.commit();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> decision.get(10, TimeUnit.SECONDS)
            );
            assertTrue(failure.getCause() instanceof LabelVersionConflictException);
            assertDecisionUnchanged();
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update(
                    "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                    originalFormulaId,
                    productId
            );
            jdbcTemplate.update(
                    "DELETE FROM formula_version WHERE formula_version_id = ?",
                    STALE_FORMULA_ID
            );
        }
    }

    @Test
    void rollsBackAllDecisionWritesWhenApprovalRecordInsertFails() {
        createPassedValidation();
        createReviewFixture();
        reviewService.submitForReview(LABEL_ID, reviewSubmitter());
        doThrow(new IllegalStateException("forced approval record failure"))
                .when(commandRepository).createApprovalRecord(
                        eq(LABEL_ID), eq(REVIEW_TASK_ID), eq("APPROVE"),
                        anyString(), anyString(), anyString());

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.recordDecision(
                        LABEL_ID,
                        "APPROVE",
                        "SCRUM-81 transaction rollback regression",
                        reviewApprover("LABEL.APPROVE")
                )
        );
        assertDecisionUnchanged();
    }

    private void assertDecisionUnchanged() {
        assertStatus("PENDING_REVIEW");
        assertReviewTaskStatus("IN_REVIEW");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE label_version_id = ?",
                Integer.class, LABEL_ID));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE entity_type = 'LABEL_VERSION' AND entity_id = ?
                  AND event_type = 'LABEL_DECISION_RECORDED'
                """, Integer.class, LABEL_ID));
    }

    @Test
    void publishesApprovedLabelAndSupersedesPreviousPublishedLabel() {
        createPassedValidation();
        createReviewFixture();

        reviewService.submitForReview(
                LABEL_ID,
                reviewSubmitter()
        );

        reviewService.recordDecision(
                LABEL_ID,
                "APPROVE",
                "SCRUM-37 publication integration test",
                reviewApprover("LABEL.APPROVE")
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

        reviewService.publishReviewTask(
                REVIEW_TASK_ID,
                LABEL_ID,
                reviewApprover("LABEL.PUBLISH")
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
        assertEquals(
                LABEL_ID,
                jdbcTemplate.queryForObject(
                        "SELECT current_published_label_version_id FROM product WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)",
                        String.class,
                        LABEL_ID
                )
        );
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM publication_record WHERE label_version_id = ?",
                Integer.class,
                LABEL_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type = 'LABEL_PUBLISHED' AND entity_id = ?",
                Integer.class,
                LABEL_ID
        ));
        assertReviewTaskStatus("CLOSED");
        assertEquals("user_approver", jdbcTemplate.queryForObject(
                "SELECT resolved_by_user_id FROM review_task WHERE review_task_id = ?",
                String.class,
                REVIEW_TASK_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id = ? AND resolved_at IS NOT NULL",
                Integer.class,
                REVIEW_TASK_ID
        ));
        assertThrows(
                IllegalStateException.class,
                () -> reviewService.publishReviewTask(
                        REVIEW_TASK_ID, LABEL_ID,
                        reviewApprover("LABEL.PUBLISH")
                )
        );
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM publication_record WHERE label_version_id = ?",
                Integer.class,
                LABEL_ID
        ));
    }

    @Test
    void rejectsPublicationWhenTargetIsNotApproved() {
        createReviewFixture();
        assertThrows(IllegalStateException.class, () ->
                reviewService.publishReviewTask(
                        REVIEW_TASK_ID, LABEL_ID,
                        reviewApprover("LABEL.PUBLISH")
                ));
        assertEquals(0, publicationCount());
    }

    @Test
    void rejectsPublicationWhenDecisionIsNotApprove() {
        createReviewFixture();
        jdbcTemplate.update(
                "UPDATE review_task SET status='IN_REVIEW', decision='REJECT' WHERE review_task_id = ?",
                REVIEW_TASK_ID
        );
        jdbcTemplate.update(
                "UPDATE label_version SET lifecycle_status='APPROVED' WHERE label_version_id = ?",
                LABEL_ID
        );
        assertThrows(IllegalStateException.class, () ->
                reviewService.publishReviewTask(
                        REVIEW_TASK_ID, LABEL_ID,
                        reviewApprover("LABEL.PUBLISH")
                ));
        assertEquals(0, publicationCount());
        assertStatus("APPROVED");
    }

    @Test
    void rejectsPublicationForMismatchedTargetVersion() {
        createReviewFixture();
        assertThrows(LabelVersionConflictException.class, () ->
                reviewService.publishReviewTask(
                        REVIEW_TASK_ID, "another_label_version",
                        reviewApprover("LABEL.PUBLISH")
                ));
        assertEquals(0, publicationCount());
    }

    @Test
    void rejectsPublicationAfterApprovedFormulaChangesWithoutAnyPublicationWrites() {
        createPassedValidation();
        createReviewFixture();
        approveTarget();

        String productId = productIdForTarget();
        String originalFormulaId = formulaIdForTarget();
        jdbcTemplate.update("""
                INSERT INTO formula_version (
                    formula_version_id, product_id, version_number,
                    lifecycle_status, is_current_released, created_by_user_id,
                    released_by_user_id, released_at, data_provenance_id
                )
                SELECT ?, product_id, version_number + 2000, 'RELEASED', 'N',
                       created_by_user_id, created_by_user_id, NOW(), data_provenance_id
                FROM formula_version WHERE formula_version_id = ?
                """, STALE_FORMULA_ID, originalFormulaId);
        jdbcTemplate.update(
                "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                STALE_FORMULA_ID, productId);

        try {
            assertThrows(LabelVersionConflictException.class, () ->
                    reviewService.publishReviewTask(
                            REVIEW_TASK_ID, LABEL_ID,
                            reviewApprover("LABEL.PUBLISH")));
            assertPublicationUnchanged(1);
        } finally {
            jdbcTemplate.update(
                    "UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                    originalFormulaId, productId);
            jdbcTemplate.update(
                    "DELETE FROM formula_version WHERE formula_version_id = ?",
                    STALE_FORMULA_ID);
        }
    }

    @Test
    void rejectsPublicationWhenNewerLabelExistsAfterApprovalWithoutAnyPublicationWrites() {
        createPassedValidation();
        createReviewFixture();
        approveTarget();
        jdbcTemplate.update("""
                INSERT INTO label_version (
                    label_version_id, product_id, formula_version_id,
                    rule_set_version_id, jurisdiction_code, version_number,
                    raw_ingredient_text, lifecycle_status, is_current_published,
                    created_by_user_id, created_at, data_provenance_id
                )
                SELECT 'label_scrum82_newer', product_id, formula_version_id,
                       rule_set_version_id, jurisdiction_code, 1001,
                       raw_ingredient_text, 'DRAFT', 'N', 'user_label_officer',
                       NOW(), data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, LABEL_ID);

        try {
            assertThrows(LabelVersionConflictException.class, () ->
                    reviewService.publishReviewTask(
                            REVIEW_TASK_ID, LABEL_ID,
                            reviewApprover("LABEL.PUBLISH")));
            assertPublicationUnchanged(1);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM label_version WHERE label_version_id = 'label_scrum82_newer'");
        }
    }

    @Test
    void rejectsPublicationWhenNewerDraftCommitsWhilePublicationWaitsForProductLock() throws Exception {
        // Existing SQL helpers supply validation/impact prerequisites. Submission and APPROVE
        // below use the real transactional service, rather than synthetic APPROVED state.
        createPassedValidation();
        createReviewFixture();
        approveTarget();
        assertStatus("APPROVED");
        assertReviewTaskStatus("IN_REVIEW");
        assertTrue(workflowRepository.findVersion(LABEL_ID).orElseThrow().current());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE review_task_id=? AND label_version_id=? AND decision='APPROVE'",
                Integer.class, REVIEW_TASK_ID, LABEL_ID));
        assertEquals(0, publicationCount());

        String productId = productIdForTarget();
        String jurisdiction = jdbcTemplate.queryForObject(
                "SELECT jurisdiction_code FROM label_version WHERE label_version_id=?", String.class, LABEL_ID);
        CountDownLatch draftCreatedHoldingProduct = new CountDownLatch(1);
        CountDownLatch allowDraftCommit = new CountDownLatch(1);
        CountDownLatch publicationEntered = new CountDownLatch(1);
        CountDownLatch publicationGuardRead = new CountDownLatch(1);
        CountDownLatch allowPublicationService = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicLong holderConnectionId = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong publisherConnectionId = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicReference<String> newerDraftId = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> publisherIsolation = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<LabelReviewCommandRepository.PublicationTarget> observedTarget =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<RuntimeException> publicationFailure = new java.util.concurrent.atomic.AtomicReference<>();

        // Observe connection-local metadata before the real repository call; this adds no
        // table read or data mutation. After its real query, pause only to snapshot committed
        // state including the intentionally created draft before publication can write.
        doAnswer(invocation -> {
            publisherConnectionId.set(jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class));
            publisherIsolation.set(jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
            publicationEntered.countDown();
            @SuppressWarnings("unchecked")
            java.util.Optional<LabelReviewCommandRepository.PublicationTarget> target =
                    (java.util.Optional<LabelReviewCommandRepository.PublicationTarget>) invocation.callRealMethod();
            observedTarget.set(target.orElseThrow());
            publicationGuardRead.countDown();
            if (!allowPublicationService.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test orchestration timed out after real publication guard read");
            }
            return target;
        }).when(commandRepository).lockForPublication(REVIEW_TASK_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = null;
        Future<?> publisher = null;
        java.util.Map<String, Object> waitEvidence;
        try (Connection observer = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())) {
            System.out.printf("PR67_RACE_CONTAINER: id=%s mysql=%s%n", MYSQL.getContainerId(), MYSQL.getDockerImageName());
            holder = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .execute(status -> {
                        holderConnectionId.set(jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        // The actual draft service locks product/current formula and creates the
                        // newer version in this outer transaction. No manual version INSERT.
                        var draft = labelDraftService.createDraft(productId, jurisdiction,
                                new AuthenticatedActor("user_label_officer", "user_label_officer", "Label Officer",
                                        Set.of(), Set.of("LABEL.CREATE")));
                        newerDraftId.set(draft.labelVersionId());
                        draftCreatedHoldingProduct.countDown();
                        try {
                            if (!allowDraftCommit.await(20, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test orchestration timed out before draft commit");
                            }
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                        return draft;
                    }));
            assertTrue(draftCreatedHoldingProduct.await(20, TimeUnit.SECONDS), "Actual draft creation did not reach held transaction");
            publisher = executor.submit(() -> {
                try {
                    reviewService.publishReviewTask(REVIEW_TASK_ID, LABEL_ID, reviewApprover("LABEL.PUBLISH"));
                } catch (RuntimeException failure) {
                    publicationFailure.set(failure);
                }
            });
            assertTrue(publicationEntered.await(10, TimeUnit.SECONDS));
            // Commit only after MySQL itself reports this exact publisher waiting on this
            // exact holder's product lock. No timing sleep substitutes for lock observation.
            waitEvidence = awaitPublicationProductLockWait(observer, publisherConnectionId.get(), holderConnectionId.get());
            System.out.printf("PR67_RACE_LOCK_WAIT: %s%n", waitEvidence);
            assertEquals("REPEATABLE-READ", publisherIsolation.get());
            allowDraftCommit.countDown();
            holder.get(20, TimeUnit.SECONDS);
            assertTrue(publicationGuardRead.await(20, TimeUnit.SECONDS), "Real publication guard query did not complete");
            int oldVersion = jdbcTemplate.queryForObject(
                    "SELECT version_number FROM label_version WHERE label_version_id=?", Integer.class, LABEL_ID);
            int newVersion = jdbcTemplate.queryForObject(
                    "SELECT version_number FROM label_version WHERE label_version_id=?", Integer.class, newerDraftId.get());
            assertTrue(newVersion > oldVersion);
            assertEquals("DRAFT", jdbcTemplate.queryForObject(
                    "SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, newerDraftId.get()));
            java.util.Map<String, Object> beforePublication = racePublicationStateSnapshot();
            allowPublicationService.countDown();
            publisher.get(20, TimeUnit.SECONDS);
            java.util.Map<String, Object> afterPublication = racePublicationStateSnapshot();
            var target = observedTarget.get();
            RuntimeException failure = publicationFailure.get();
            System.out.printf("PR67_RACE_RESULT: holder=%d publisher=%d isolation=%s oldVersion=%d newVersion=%d "
                            + "guardCurrentFormula=%s guardLatest=%s guardApproveRecord=%s exception=%s "
                            + "label=%s task=%s publicationRecords=%d publicationAudits=%d unchanged=%s%n",
                    holderConnectionId.get(), publisherConnectionId.get(), publisherIsolation.get(), oldVersion, newVersion,
                    target.currentFormula(), target.latestLabelVersion(), target.hasApproveRecord(),
                    failure == null ? "none" : failure.getClass().getSimpleName(),
                    jdbcTemplate.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, LABEL_ID),
                    jdbcTemplate.queryForObject("SELECT status FROM review_task WHERE review_task_id=?", String.class, REVIEW_TASK_ID),
                    publicationCount(), jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM audit_event WHERE event_type='LABEL_PUBLISHED' AND entity_id=?", Integer.class, LABEL_ID),
                    beforePublication.equals(afterPublication));
            org.junit.jupiter.api.Assertions.assertAll("Concurrent draft must invalidate publication after lock acquisition",
                    () -> assertFalse(target.latestLabelVersion(), "Guard must read newly committed draft after product lock wait"),
                    () -> assertTrue(failure instanceof LabelVersionConflictException, "Actual publication must reject stale label; actual=" + failure),
                    () -> assertEquals(beforePublication, afterPublication,
                            "All label rows, product fields, task, approvals, publication and audit rows must remain unchanged"),
                    () -> assertPublicationUnchanged(1));
        } finally {
            allowDraftCommit.countDown();
            allowPublicationService.countDown();
            if (holder != null) {
                try { holder.get(20, TimeUnit.SECONDS); } catch (Exception ignored) { }
            }
            if (publisher != null) {
                try { publisher.get(20, TimeUnit.SECONDS); } catch (Exception ignored) { }
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Owned race workers must stop before fixture cleanup");
            if (newerDraftId.get() != null) {
                jdbcTemplate.update("DELETE FROM label_version WHERE label_version_id=?", newerDraftId.get());
            }
        }
    }

    private java.util.Map<String, Object> awaitPublicationProductLockWait(Connection observer, long publisherId, long holderId)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        String sql = """
                SELECT waiter.PROCESSLIST_ID AS publisher_connection_id,
                       holder.PROCESSLIST_ID AS holder_connection_id,
                       requested.OBJECT_SCHEMA, requested.OBJECT_NAME,
                       requested.INDEX_NAME, requested.LOCK_TYPE, requested.LOCK_MODE,
                       requested.LOCK_STATUS, requested.LOCK_DATA
                FROM performance_schema.data_lock_waits waits
                JOIN performance_schema.threads waiter ON waiter.THREAD_ID = waits.REQUESTING_THREAD_ID
                JOIN performance_schema.threads holder ON holder.THREAD_ID = waits.BLOCKING_THREAD_ID
                JOIN performance_schema.data_locks requested
                  ON requested.ENGINE = waits.ENGINE
                 AND requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
                WHERE waiter.PROCESSLIST_ID = ? AND holder.PROCESSLIST_ID = ?
                  AND requested.OBJECT_SCHEMA = 'spectrace' AND requested.OBJECT_NAME = 'product'
                """;
        while (System.nanoTime() < deadline) {
            try (PreparedStatement query = observer.prepareStatement(sql)) {
                query.setLong(1, publisherId);
                query.setLong(2, holderId);
                try (java.sql.ResultSet rows = query.executeQuery()) {
                    if (rows.next()) {
                        java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
                        for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                            evidence.put(rows.getMetaData().getColumnLabel(column), rows.getObject(column));
                        }
                        return evidence;
                    }
                }
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new java.util.concurrent.TimeoutException("MySQL did not report expected product row lock wait: publisher="
                + publisherId + ", holder=" + holderId);
    }

    private java.util.Map<String, Object> racePublicationStateSnapshot() {
        return java.util.Map.of(
                "labels", jdbcTemplate.queryForList("""
                        SELECT * FROM label_version WHERE product_id =
                            (SELECT product_id FROM label_version WHERE label_version_id = ?)
                        ORDER BY label_version_id
                        """, LABEL_ID),
                "product", jdbcTemplate.queryForMap("""
                        SELECT * FROM product WHERE product_id =
                            (SELECT product_id FROM label_version WHERE label_version_id = ?)
                        """, LABEL_ID),
                "task", jdbcTemplate.queryForMap("SELECT * FROM review_task WHERE review_task_id = ?", REVIEW_TASK_ID),
                "approvals", jdbcTemplate.queryForList("SELECT * FROM approval_record WHERE label_version_id = ? ORDER BY approval_record_id", LABEL_ID),
                "publications", jdbcTemplate.queryForList("SELECT * FROM publication_record WHERE label_version_id = ? ORDER BY publication_record_id", LABEL_ID),
                "audits", jdbcTemplate.queryForList("SELECT * FROM audit_event WHERE entity_type = 'LABEL_VERSION' AND entity_id = ? ORDER BY audit_event_id", LABEL_ID));
    }

    static {
        // Other classes share this test-JVM fixture; release it after every
        // selected class has finished instead of invalidating later contexts.
        Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop, "workflow-test-mysql-cleanup"));
    }

    @Test
    void rejectsPublicationWithoutAssociatedApproveRecordWithoutAnyPublicationWrites() {
        createPassedValidation();
        createReviewFixture();
        approveTarget();
        jdbcTemplate.update(
                "DELETE FROM approval_record WHERE review_task_id = ? AND label_version_id = ?",
                REVIEW_TASK_ID, LABEL_ID);

        assertThrows(IllegalStateException.class, () ->
                reviewService.publishReviewTask(
                        REVIEW_TASK_ID, LABEL_ID,
                        reviewApprover("LABEL.PUBLISH")));
        assertPublicationUnchanged(0);
    }

    @Test
    void firstPublicationWorksWithoutPreviousPublishedVersion() {
        createPassedValidation();
        createReviewFixture();
        jdbcTemplate.update("""
                UPDATE label_version
                SET lifecycle_status='SUPERSEDED', is_current_published='N'
                WHERE product_id = (SELECT product_id FROM
                    (SELECT product_id FROM label_version WHERE label_version_id = ?) chosen)
                  AND jurisdiction_code = (SELECT jurisdiction_code FROM
                    (SELECT jurisdiction_code FROM label_version WHERE label_version_id = ?) chosen)
                  AND label_version_id <> ? AND lifecycle_status='PUBLISHED'
                """, LABEL_ID, LABEL_ID, LABEL_ID);
        jdbcTemplate.update("""
                UPDATE product
                SET current_published_label_version_id = NULL
                WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)
                """, LABEL_ID);
        approveTarget();

        reviewService.publishReviewTask(
                REVIEW_TASK_ID, LABEL_ID, reviewApprover("LABEL.PUBLISH")
        );

        assertStatus("PUBLISHED");
        assertEquals(LABEL_ID, jdbcTemplate.queryForObject(
                "SELECT current_published_label_version_id FROM product WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)",
                String.class, LABEL_ID));
    }

    @Test
    void publicationFailureRollsBackLifecyclePointerRecordsAndResolution() {
        createPassedValidation();
        createReviewFixture();
        approveTarget();
        String oldPublishedLabelId = jdbcTemplate.queryForObject(
                "SELECT label_version_id FROM label_version WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?) AND jurisdiction_code = (SELECT jurisdiction_code FROM label_version WHERE label_version_id = ?) AND lifecycle_status='PUBLISHED' AND is_current_published='Y' AND label_version_id <> ? LIMIT 1",
                String.class, LABEL_ID, LABEL_ID, LABEL_ID);
        String oldPointer = jdbcTemplate.queryForObject(
                "SELECT current_published_label_version_id FROM product WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)",
                String.class, LABEL_ID);
        doThrow(new IllegalStateException("forced publication audit failure"))
                .when(commandRepository).createPublicationAudit(
                        eq(LABEL_ID), eq(REVIEW_TASK_ID), anyString(), anyString());

        assertThrows(IllegalStateException.class, () ->
                reviewService.publishReviewTask(
                        REVIEW_TASK_ID, LABEL_ID,
                        reviewApprover("LABEL.PUBLISH")
                ));

        assertStatus("APPROVED");
        assertReviewTaskStatus("IN_REVIEW");
        assertEquals(oldPointer, jdbcTemplate.queryForObject(
                "SELECT current_published_label_version_id FROM product WHERE product_id = (SELECT product_id FROM label_version WHERE label_version_id = ?)",
                String.class, LABEL_ID));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class, oldPublishedLabelId));
        assertEquals("N", jdbcTemplate.queryForObject(
                "SELECT is_current_published FROM label_version WHERE label_version_id = ?",
                String.class, LABEL_ID));
        assertEquals(0, publicationCount());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type='LABEL_PUBLISHED' AND entity_id = ?",
                Integer.class, LABEL_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id = ? AND resolved_at IS NOT NULL",
                Integer.class, REVIEW_TASK_ID));
    }

    private void approveTarget() {
        reviewService.submitForReview(LABEL_ID, reviewSubmitter());
        reviewService.recordDecision(
                LABEL_ID, "APPROVE", "approved", reviewApprover("LABEL.APPROVE")
        );
    }

    private String productIdForTarget() {
        return jdbcTemplate.queryForObject(
                "SELECT product_id FROM label_version WHERE label_version_id = ?",
                String.class, LABEL_ID);
    }

    private String formulaIdForTarget() {
        return jdbcTemplate.queryForObject(
                "SELECT formula_version_id FROM label_version WHERE label_version_id = ?",
                String.class, LABEL_ID);
    }

    private void assertPublicationUnchanged(int expectedApprovalCount) {
        assertStatus("APPROVED");
        assertReviewTaskStatus("IN_REVIEW");
        assertEquals(0, publicationCount());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type='LABEL_PUBLISHED' AND entity_id=?",
                Integer.class, LABEL_ID));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE entity_type='LABEL_VERSION' AND entity_id=?",
                Integer.class, LABEL_ID));
        assertEquals(expectedApprovalCount, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE review_task_id=? AND label_version_id=? AND decision='APPROVE'",
                Integer.class, REVIEW_TASK_ID, LABEL_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE review_task_id=? AND resolved_at IS NOT NULL",
                Integer.class, REVIEW_TASK_ID));
        assertEquals("APPROVE", jdbcTemplate.queryForObject(
                "SELECT decision FROM review_task WHERE review_task_id=?",
                String.class, REVIEW_TASK_ID));
        assertEquals(jdbcTemplate.queryForObject(
                        "SELECT current_label_version_id FROM review_task WHERE review_task_id=?",
                        String.class, REVIEW_TASK_ID),
                jdbcTemplate.queryForObject(
                        "SELECT current_published_label_version_id FROM product WHERE product_id=?",
                        String.class, productIdForTarget()));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM label_version WHERE label_version_id=(SELECT current_label_version_id FROM review_task WHERE review_task_id=?)",
                String.class, REVIEW_TASK_ID));
        assertEquals("N", jdbcTemplate.queryForObject(
                "SELECT is_current_published FROM label_version WHERE label_version_id=?",
                String.class, LABEL_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM label_version WHERE label_version_id=? AND lifecycle_status='SUPERSEDED'",
                Integer.class, LABEL_ID));
    }

    private int publicationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM publication_record WHERE label_version_id = ?",
                Integer.class,
                LABEL_ID
        );
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
                    idempotency_key,
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
                "impact-analysis:" + CHANGE_REQUEST_ID,
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
                    target_label_version_id,
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
                LABEL_ID,
                IMPACT_FINDING_ID
        );
    }

    private void createPassedValidation() {
        createPassedValidation(LABEL_ID);
    }

    private void createPassedValidation(String labelVersionId) {
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
                "validation_run_scrum37_" + labelVersionId,
                labelVersionId
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
        String linkedDraftId = jdbcTemplate.query(
                "SELECT COALESCE(draft_label_version_id, target_label_version_id) AS label_id FROM review_task WHERE review_task_id = ?",
                rs -> rs.next() ? rs.getString("label_id") : null,
                REVIEW_TASK_ID
        );
        jdbcTemplate.update(
                "DELETE FROM audit_event WHERE entity_type = 'LABEL_VERSION' AND (entity_id = ? OR entity_id = ?)",
                LABEL_ID,
                linkedDraftId
        );

        jdbcTemplate.update(
                "DELETE FROM approval_record WHERE review_task_id = ? OR label_version_id = ?",
                REVIEW_TASK_ID,
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM publication_record WHERE label_version_id = ? OR label_version_id = ?",
                LABEL_ID,
                linkedDraftId
        );

        jdbcTemplate.update(
                "DELETE FROM validation_result WHERE validation_run_id IN (SELECT validation_run_id FROM validation_run WHERE label_version_id = ? OR label_version_id = ?)",
                LABEL_ID,
                linkedDraftId
        );

        jdbcTemplate.update(
                "DELETE FROM validation_run WHERE label_version_id = ? OR label_version_id = ?",
                LABEL_ID,
                linkedDraftId
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
                "UPDATE product SET current_published_label_version_id = NULL " +
                        "WHERE current_published_label_version_id = ?",
                LABEL_ID
        );

        jdbcTemplate.update(
                "DELETE FROM label_version WHERE label_version_id = ? OR label_version_id = ?",
                LABEL_ID,
                linkedDraftId
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

    private AuthenticatedActor reviewSubmitter() {
        return new AuthenticatedActor(
                "user_label_officer",
                "user_label_officer",
                "Label Officer",
                Set.of(),
                Set.of("LABEL.SUBMIT_REVIEW")
        );
    }

    private AuthenticatedActor reviewApprover(
            String permission
    ) {
        return new AuthenticatedActor(
                "user_approver",
                "user_approver",
                "Approver",
                Set.of(),
                Set.of(permission)
        );
    }

    private void assertReviewTaskStatus(
            String expectedStatus
    ) {
        assertEquals(
                expectedStatus,
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
    }
}
