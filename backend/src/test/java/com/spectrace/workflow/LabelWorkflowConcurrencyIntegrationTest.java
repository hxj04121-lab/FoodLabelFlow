package com.spectrace.workflow;

import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.LabelWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LabelWorkflowConcurrencyIntegrationTest
        extends MySqlIntegrationTestSupport {

    private static final String LABEL_ID =
            "label_scrum39_concurrency";

    private static final String NEWER_LABEL_ID =
            "label_scrum39_concurrency_newer";

    private static final String COMPETING_FORMULA_ID =
            "formula_scrum39_concurrency";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LabelWorkflowService workflowService;

    @Test
    void rejectsSubmitWhenNewerDraftWinsRace()
            throws Exception {

        createDraftFixture();
        createPassedValidation();

        AuthenticatedActor actor = submitter();

        /*
         * The application observes the label as current before
         * the competing transaction invalidates it.
         */
        assertTrue(currentFlag(LABEL_ID));

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        CountDownLatch competingWriteStarted =
                new CountDownLatch(1);

        CountDownLatch allowCompetingCommit =
                new CountDownLatch(1);

        Future<?> competingWrite = executor.submit(() -> {
            try (Connection connection =
                         dataSource.getConnection()) {

                connection.setAutoCommit(false);

                /*
                 * Take the same product serialization lock used by
                 * the production workflow guard.
                 */
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     """
                                     SELECT product_id
                                     FROM product
                                     WHERE product_id = (
                                         SELECT product_id
                                         FROM label_version
                                         WHERE label_version_id = ?
                                     )
                                     FOR UPDATE
                                     """
                             )) {

                    statement.setString(
                            1,
                            LABEL_ID
                    );

                    statement.executeQuery();
                }

                competingWriteStarted.countDown();

                if (!allowCompetingCommit.await(
                        10,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "Timed out waiting to commit newer draft"
                    );
                }

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

            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });

        try {
            assertTrue(
                    competingWriteStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            /*
             * B commits the newer version after A has already
             * observed the old label as current.
             */
            allowCompetingCommit.countDown();

            competingWrite.get(
                    10,
                    TimeUnit.SECONDS
            );

            assertThrows(
                    LabelVersionConflictException.class,
                    () -> workflowService.submitForReview(
                            LABEL_ID,
                            actor
                    )
            );

            assertNoWorkflowSideEffects("DRAFT");

        } finally {
            allowCompetingCommit.countDown();
            executor.shutdownNow();
            cleanUp();
        }
    }

    @Test
    void rejectsSubmitWhenCurrentFormulaChangesAfterRead()
            throws Exception {

        createDraftFixture();
        createPassedValidation();

        AuthenticatedActor actor = submitter();

        /*
         * Again prove the original label is current before the
         * competing formula-pointer change.
         */
        assertTrue(currentFlag(LABEL_ID));

        String productId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT product_id
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        LABEL_ID
                );

        String originalFormulaId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT current_formula_version_id
                        FROM product
                        WHERE product_id = ?
                        """,
                        String.class,
                        productId
                );

        /*
         * The fixture may contain only one formula version.
         * Create a competing released formula explicitly instead
         * of assuming another version already exists.
         */
        jdbcTemplate.update(
        """
        INSERT INTO formula_version (
            formula_version_id,
            product_id,
            version_number,
            lifecycle_status,
            is_current_released,
            created_by_user_id,
            released_by_user_id,
            released_at,
            data_provenance_id
        )
        SELECT
            ?,
            product_id,
            version_number + 1000,
            'RELEASED',
            'N',
            created_by_user_id,
            created_by_user_id,
            NOW(),
            data_provenance_id
        FROM formula_version
        WHERE formula_version_id = ?
        """,
        COMPETING_FORMULA_ID,
        originalFormulaId
);

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        CountDownLatch competingWriteStarted =
                new CountDownLatch(1);

        CountDownLatch allowCompetingCommit =
                new CountDownLatch(1);

        Future<?> competingWrite = executor.submit(() -> {
            try (Connection connection =
                         dataSource.getConnection()) {

                connection.setAutoCommit(false);

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     """
                                     SELECT product_id
                                     FROM product
                                     WHERE product_id = ?
                                     FOR UPDATE
                                     """
                             )) {

                    statement.setString(
                            1,
                            productId
                    );

                    statement.executeQuery();
                }

                competingWriteStarted.countDown();

                if (!allowCompetingCommit.await(
                        10,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "Timed out waiting to switch formula"
                    );
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     """
                                     UPDATE product
                                     SET current_formula_version_id = ?
                                     WHERE product_id = ?
                                     """
                             )) {

                    statement.setString(
                            1,
                            COMPETING_FORMULA_ID
                    );

                    statement.setString(
                            2,
                            productId
                    );

                    assertEquals(
                            1,
                            statement.executeUpdate()
                    );
                }

                connection.commit();

            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });

        try {
            assertTrue(
                    competingWriteStarted.await(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            /*
             * Let the competing transaction switch the product
             * formula after the original current check.
             */
            allowCompetingCommit.countDown();

            competingWrite.get(
                    10,
                    TimeUnit.SECONDS
            );

            assertThrows(
                    LabelVersionConflictException.class,
                    () -> workflowService.submitForReview(
                            LABEL_ID,
                            actor
                    )
            );

            assertNoWorkflowSideEffects("DRAFT");

        } finally {
            allowCompetingCommit.countDown();
            executor.shutdownNow();

            /*
             * Restore the product before deleting the temporary
             * formula because of the foreign-key relationship.
             */
            jdbcTemplate.update(
                    """
                    UPDATE product
                    SET current_formula_version_id = ?
                    WHERE product_id = ?
                    """,
                    originalFormulaId,
                    productId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM formula_version
                    WHERE formula_version_id = ?
                    """,
                    COMPETING_FORMULA_ID
            );

            cleanUp();
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
                    WHERE existing.product_id = lv.product_id
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
            WHERE p.product_id = 'prod_usda_1106285'
              AND p.current_formula_version_id =
                  'formula_1106285_v1'
            """,
            LABEL_ID
    );

    assertEquals(
            1,
            jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM label_version
                    WHERE label_version_id = ?
                    """,
                    Integer.class,
                    LABEL_ID
            )
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
                    'validation_scrum39_concurrency',
                    label_version_id,
                    rule_set_version_id,
                    'PASSED',
                    'user_label_officer',
                    NOW(),
                    'SCRUM-39 concurrency regression',
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                LABEL_ID
        );
    }

    private boolean currentFlag(
            String labelVersionId
    ) {
        Boolean current =
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                            lv.formula_version_id =
                                p.current_formula_version_id
                            AND NOT EXISTS (
                                SELECT 1
                                FROM label_version newer
                                WHERE newer.product_id =
                                      lv.product_id
                                  AND newer.jurisdiction_code =
                                      lv.jurisdiction_code
                                  AND newer.version_number >
                                      lv.version_number
                            )
                        FROM label_version lv
                        JOIN product p
                          ON p.product_id = lv.product_id
                        WHERE lv.label_version_id = ?
                        """,
                        Boolean.class,
                        labelVersionId
                );

        return Boolean.TRUE.equals(current);
    }

    private void assertNoWorkflowSideEffects(
            String expectedStatus
    ) {
        String status =
                jdbcTemplate.queryForObject(
                        """
                        SELECT lifecycle_status
                        FROM label_version
                        WHERE label_version_id = ?
                        """,
                        String.class,
                        LABEL_ID
                );

        assertEquals(
                expectedStatus,
                status
        );

        Integer approvalCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM approval_record
                        WHERE label_version_id = ?
                        """,
                        Integer.class,
                        LABEL_ID
                );

        assertEquals(
                0,
                approvalCount
        );

        Integer auditCount =
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
                );

        assertEquals(
                0,
                auditCount
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
                DELETE FROM label_version
                WHERE label_version_id IN (?, ?)
                """,
                NEWER_LABEL_ID,
                LABEL_ID
        );
    }
}