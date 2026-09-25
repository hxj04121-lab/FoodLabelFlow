package com.spectrace.workflow;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.LabelWorkflowService;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import com.spectrace.workflow.infrastructure.JdbcLabelWorkflowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LabelWorkflowAtomicGuardIntegrationTest
        extends MySqlIntegrationTestSupport {

    private static final String LABEL_ID =
            "label_scrum39_atomic";

    private static final String NEWER_LABEL_ID =
            "label_scrum39_atomic_newer";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private MakerCheckerPolicy makerCheckerPolicy;

    @Test
    void databaseGuardRejectsNewerVersionCreatedAfterServicePrecheck()
            throws Exception {

        createDraftFixture();
        createPassedValidation();

        JdbcLabelWorkflowRepository realRepository =
                new JdbcLabelWorkflowRepository(jdbcTemplate);

        CountDownLatch precheckCompleted =
                new CountDownLatch(1);

        CountDownLatch allowWrite =
                new CountDownLatch(1);

        LabelWorkflowRepository interceptingRepository =
                new LabelWorkflowRepository() {

                    @Override
                    public Optional<String> findCreatorUserId(
                            String labelVersionId
                    ) {
                        return realRepository.findCreatorUserId(
                                labelVersionId
                        );
                    }

                    @Override
                    public Optional<LabelWorkflowVersion> findVersion(
                            String labelVersionId
                    ) {
                        Optional<LabelWorkflowVersion> version =
                                realRepository.findVersion(
                                        labelVersionId
                                );

                        precheckCompleted.countDown();

                        try {
                            if (!allowWrite.await(
                                    10,
                                    TimeUnit.SECONDS
                            )) {
                                throw new IllegalStateException(
                                        "Timed out waiting for competing write"
                                );
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(error);
                        }

                        return version;
                    }

                    @Override
                    public void submitForReview(
                            String labelVersionId,
                            String actorUserId
                    ) {
                        realRepository.submitForReview(
                                labelVersionId,
                                actorUserId
                        );
                    }

                    @Override
                    public void recordDecision(
                            String labelVersionId,
                            String decision,
                            String actorUserId,
                            String comments
                    ) {
                        realRepository.recordDecision(
                                labelVersionId,
                                decision,
                                actorUserId,
                                comments
                        );
                    }
                };

        LabelWorkflowService service =
                new LabelWorkflowService(
                        authorizationService,
                        interceptingRepository,
                        makerCheckerPolicy
                );

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        Future<?> serviceCall = executor.submit(() ->
                service.submitForReview(
                        LABEL_ID,
                        submitter()
                )
        );

        try {
            assertTrue(
                    precheckCompleted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            /*
             * Service has already obtained current=true from
             * findVersion(), but has not reached the stored
             * procedure yet.
             */
            createNewerVersionInIndependentTransaction();

            allowWrite.countDown();

            Exception failure = assertThrows(
                    Exception.class,
                    () -> serviceCall.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            Throwable cause = failure.getCause();

            assertTrue(
                    cause instanceof LabelVersionConflictException,
                    "DB guard must reject the version after "
                            + "the Java pre-check already passed"
            );

            assertNoSubmitSideEffects();

        } finally {
            allowWrite.countDown();
            executor.shutdownNow();
            cleanUp();
        }
    }

    private void createNewerVersionInIndependentTransaction()
            throws Exception {

        try (Connection connection =
                     dataSource.getConnection()) {

            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(
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
                                     version_number + 1,
                                     raw_ingredient_text,
                                     'DRAFT',
                                     'N',
                                     'user_label_officer',
                                     NOW(),
                                     data_provenance_id
                                 FROM label_version
                                 WHERE label_version_id = ?
                                 """
                         )) {

                statement.setString(
                        1,
                        NEWER_LABEL_ID
                );

                statement.setString(
                        2,
                        LABEL_ID
                );

                assertEquals(
                        1,
                        statement.executeUpdate()
                );
            }

            connection.commit();
        }
    }

    private void createDraftFixture() {
        cleanUp();

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
                    lv.product_id,
                    p.current_formula_version_id,
                    lv.rule_set_version_id,
                    lv.jurisdiction_code,
                    COALESCE((
                        SELECT MAX(existing.version_number)
                        FROM label_version existing
                        WHERE existing.product_id =
                              lv.product_id
                          AND existing.jurisdiction_code =
                              lv.jurisdiction_code
                    ), 0) + 1,
                    lv.raw_ingredient_text,
                    'DRAFT',
                    'N',
                    'user_label_officer',
                    NOW(),
                    lv.data_provenance_id
                FROM product p
                JOIN label_version lv
                  ON lv.label_version_id =
                     p.current_published_label_version_id
                WHERE p.product_id =
                      'prod_usda_1106285'
                  AND p.current_formula_version_id =
                      'formula_1106285_v1'
                """,
                LABEL_ID
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
                    'validation_scrum39_atomic',
                    label_version_id,
                    rule_set_version_id,
                    'PASSED',
                    'user_label_officer',
                    NOW(),
                    'SCRUM-39 atomic guard regression',
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                LABEL_ID
        );
    }

    private void assertNoSubmitSideEffects() {
        assertEquals(
                "DRAFT",
                jdbcTemplate.queryForObject(
                        """
                        SELECT lifecycle_status
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        LABEL_ID
                )
        );

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM approval_record
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
                          AND event_type IN (
                              'LABEL_PENDING_REVIEW',
                              'LABEL_DECISION_RECORDED'
                          )
                        """,
                        Integer.class,
                        LABEL_ID
                )
        );
    }

    private AuthenticatedActor submitter() {
        return new AuthenticatedActor(
                "user_label_officer",
                "user_label_officer",
                "Label Officer",
                Set.of(),
                Set.of("LABEL.SUBMIT_REVIEW")
        );
    }

    private void cleanUp() {
        jdbcTemplate.update(
                """
                DELETE FROM approval_record
                WHERE label_version_id IN (?, ?)
                """,
                LABEL_ID,
                NEWER_LABEL_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM audit_event
                WHERE entity_type = 'LABEL_VERSION'
                  AND entity_id IN (?, ?)
                """,
                LABEL_ID,
                NEWER_LABEL_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM validation_result
                WHERE validation_run_id IN (
                    SELECT validation_run_id
                    FROM validation_run
                    WHERE label_version_id IN (?, ?)
                )
                """,
                LABEL_ID,
                NEWER_LABEL_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM validation_run
                WHERE label_version_id IN (?, ?)
                """,
                LABEL_ID,
                NEWER_LABEL_ID
        );
jdbcTemplate.update(
        """
        DELETE FROM publication_record
        WHERE label_version_id IN (?, ?)
        """,
        LABEL_ID,
        NEWER_LABEL_ID
);

        jdbcTemplate.update(
                """
                DELETE FROM label_version
                WHERE label_version_id IN (?, ?)
                """,
                NEWER_LABEL_ID,
                LABEL_ID
        );
    }
@Test
void databaseGuardRejectsPublishingApprovedHistoricalVersion()
        throws Exception {

    createDraftFixture();

    jdbcTemplate.update(
            """
            UPDATE label_version
            SET lifecycle_status = 'APPROVED'
            WHERE label_version_id = ?
            """,
            LABEL_ID
    );

    createNewerVersionInIndependentTransaction();

    assertThrows(
            LabelVersionConflictException.class,
            () -> {
                JdbcLabelWorkflowRepository repository =
                        new JdbcLabelWorkflowRepository(
                                jdbcTemplate
                        );

                /*
                 * Publish is executed by the database procedure.
                 * The repository does not expose publish, so invoke
                 * the procedure through JdbcTemplate and translate
                 * its atomic conflict signal here.
                 */
                try {
                    jdbcTemplate.update(
                            "CALL sp_publish_label(?, ?)",
                            LABEL_ID,
                            "user_label_officer"
                    );
                } catch (org.springframework.dao.DataAccessException error) {
                    Throwable cause =
                            error.getMostSpecificCause();

                    String message =
                            cause == null
                                    ? error.getMessage()
                                    : cause.getMessage();

                    if (message != null
                            && message.contains(
                                    "LABEL_VERSION_CONFLICT"
                            )) {
                        throw new LabelVersionConflictException(
                                "Label version is stale or historical"
                        );
                    }

                    throw error;
                }
            }
    );

    assertEquals(
            "APPROVED",
            jdbcTemplate.queryForObject(
                    """
                    SELECT lifecycle_status
                    FROM label_version
                    WHERE label_version_id = ?
                    """,
                    String.class,
                    LABEL_ID
            )
    );

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

    cleanUp();
}
}