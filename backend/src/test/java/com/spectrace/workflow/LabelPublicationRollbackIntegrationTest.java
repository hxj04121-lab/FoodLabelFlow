package com.spectrace.workflow;

import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.workflow.application.LabelPublicationService;
import com.spectrace.workflow.application.port.LabelPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LabelPublicationRollbackIntegrationTest
        extends MySqlIntegrationTestSupport {

    @Autowired
    private LabelPublicationService publicationService;

@Autowired
private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private LabelPublicationRepository publicationRepository;

    @Test
    void rollsBackEntirePublicationWhenPublicationRecordFails() {
        String labelId = "label_publication_rollback_test";

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
                    9998,
                    lv.raw_ingredient_text,
                    'APPROVED',
                    'N',
                    'user_label_officer',
                    NOW(),
                    lv.data_provenance_id
                FROM label_version lv
                JOIN product p
                  ON p.product_id = lv.product_id
                WHERE lv.lifecycle_status = 'PUBLISHED'
                  AND lv.is_current_published = 'Y'
                LIMIT 1
                """,
                labelId
        );

        String productId = jdbcTemplate.queryForObject(
                """
                SELECT product_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                String.class,
                labelId
        );

        String jurisdiction = jdbcTemplate.queryForObject(
                """
                SELECT jurisdiction_code
                FROM label_version
                WHERE label_version_id = ?
                """,
                String.class,
                labelId
        );

        String oldPublishedLabelId = jdbcTemplate.queryForObject(
                """
                SELECT label_version_id
                FROM label_version
                WHERE product_id = ?
                  AND jurisdiction_code = ?
                  AND lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y'
                LIMIT 1
                """,
                String.class,
                productId,
                jurisdiction
        );

        jdbcTemplate.update(
                """
                INSERT INTO approval_record (
                    approval_record_id,
                    label_version_id,
                    review_task_id,
                    decision,
                    decided_by_user_id,
                    decided_at,
                    comments,
                    data_provenance_id
                )
                SELECT
                    ?,
                    ?,
                    rt.review_task_id,
                    'APPROVE',
                    'user_approver',
                    NOW(),
                    'SCRUM-50 rollback fixture',
                    lv.data_provenance_id
                FROM label_version lv
                JOIN review_task rt
                  ON rt.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                LIMIT 1
                """,
                "approval_publication_rollback_test",
                labelId,
                labelId
        );

        doThrow(
                new IllegalStateException(
                        "forced publication record failure"
                )
        ).when(publicationRepository)
                .createPublicationRecord(
                        anyString(),
                        anyString(),
                        anyString()
                );

        AuthenticatedActor publisher =
                new AuthenticatedActor(
                        "user_publisher",
                        "user_publisher",
                        "Publisher",
                        Set.of(),
                        Set.of("LABEL.PUBLISH")
                );

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> publicationService.publishLabel(
                            labelId,
                            publisher
                    )
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
                            labelId
                    )
            );

            assertEquals(
                    "PUBLISHED",
                    jdbcTemplate.queryForObject(
                            """
                            SELECT lifecycle_status
                            FROM label_version
                            WHERE label_version_id = ?
                            """,
                            String.class,
                            oldPublishedLabelId
                    )
            );

            assertEquals(
                    "Y",
                    jdbcTemplate.queryForObject(
                            """
                            SELECT is_current_published
                            FROM label_version
                            WHERE label_version_id = ?
                            """,
                            String.class,
                            oldPublishedLabelId
                    )
            );

            assertEquals(
                    oldPublishedLabelId,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT current_published_label_version_id
                            FROM product
                            WHERE product_id = ?
                            """,
                            String.class,
                            productId
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
                            labelId
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
                            labelId
                    )
            );
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM approval_record WHERE label_version_id = ?",
                    labelId
            );

            jdbcTemplate.update(
                    "DELETE FROM publication_record WHERE label_version_id = ?",
                    labelId
            );

            jdbcTemplate.update(
                    "DELETE FROM audit_event WHERE entity_type = 'LABEL_VERSION' AND entity_id = ?",
                    labelId
            );

            jdbcTemplate.update(
                    "UPDATE product SET current_published_label_version_id = ? WHERE product_id = ?",
                    oldPublishedLabelId,
                    productId
            );

            jdbcTemplate.update(
                    "DELETE FROM label_version WHERE label_version_id = ?",
                    labelId
            );
        }
    }
}