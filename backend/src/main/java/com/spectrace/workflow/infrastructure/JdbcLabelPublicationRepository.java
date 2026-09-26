package com.spectrace.workflow.infrastructure;

import com.spectrace.workflow.application.port.LabelPublicationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLabelPublicationRepository
        implements LabelPublicationRepository {

    private final JdbcTemplate jdbc;

    public JdbcLabelPublicationRepository(
            JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublicationTarget> lockForPublication(
            String labelVersionId
    ) {
        return jdbc.query(
                """
                SELECT
                    lv.label_version_id,
                    lv.product_id,
                    lv.formula_version_id,
                    p.current_formula_version_id,
                    lv.jurisdiction_code,
                    lv.lifecycle_status,
                    lv.data_provenance_id
                FROM label_version lv
                JOIN product p
                  ON p.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                FOR UPDATE
                """,
                rs -> rs.next()
                        ? Optional.of(
                                new PublicationTarget(
                                        rs.getString(
                                                "label_version_id"
                                        ),
                                        rs.getString(
                                                "product_id"
                                        ),
                                        rs.getString(
                                                "formula_version_id"
                                        ),
                                        rs.getString(
                                                "current_formula_version_id"
                                        ),
                                        rs.getString(
                                                "jurisdiction_code"
                                        ),
                                        rs.getString(
                                                "lifecycle_status"
                                        ),
                                        rs.getString(
                                                "data_provenance_id"
                                        )
                                )
                        )
                        : Optional.empty(),
                labelVersionId
        );
    }

    @Override
    public boolean hasApproveRecord(
            String labelVersionId
    ) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM approval_record
                WHERE label_version_id = ?
                  AND decision = 'APPROVE'
                """,
                Integer.class,
                labelVersionId
        );

        return count != null && count > 0;
    }

    @Override
    public void supersedeCurrentPublished(
            String productId,
            String jurisdictionCode
    ) {
        jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'SUPERSEDED',
                    is_current_published = 'N'
                WHERE product_id = ?
                  AND jurisdiction_code = ?
                  AND lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y'
                """,
                productId,
                jurisdictionCode
        );
    }

    @Override
    public int markPublished(
            String labelVersionId
    ) {
        return jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'PUBLISHED',
                    is_current_published = 'Y'
                WHERE label_version_id = ?
                  AND lifecycle_status = 'APPROVED'
                """,
                labelVersionId
        );
    }

    @Override
    public void updateCurrentPublishedPointer(
            String productId,
            String labelVersionId
    ) {
        jdbc.update(
                """
                UPDATE product
                SET current_published_label_version_id = ?
                WHERE product_id = ?
                """,
                labelVersionId,
                productId
        );
    }

    @Override
    public void linkAndResolveReviewTask(
            String labelVersionId
    ) {
        jdbc.update(
                """
                UPDATE review_task rt
                JOIN approval_record ar
                  ON ar.review_task_id = rt.review_task_id
                SET rt.target_label_version_id = ?,
                    rt.status = 'CLOSED'
                WHERE ar.label_version_id = ?
                  AND ar.decision = 'APPROVE'
                """,
                labelVersionId,
                labelVersionId
        );
    }
    @Override
    public void createPublicationRecord(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    ) {
        jdbc.update(
                """
                INSERT INTO publication_record (
                    publication_record_id,
                    label_version_id,
                    published_by_user_id,
                    published_at,
                    publication_channel,
                    data_provenance_id
                )
                VALUES (?, ?, ?, NOW(), ?, ?)
                """,
                "publication_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", ""),
                labelVersionId,
                actorUserId,
                "DEMO_RELEASE",
                dataProvenanceId
        );
    }

    @Override
    public void createPublicationAudit(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    ) {
        jdbc.update(
                """
                INSERT INTO audit_event (
                    audit_event_id,
                    event_type,
                    entity_type,
                    entity_id,
                    event_at,
                    actor_user_id,
                    before_value,
                    after_value,
                    event_payload,
                    correlation_id,
                    data_provenance_id
                )
                VALUES (
                    ?,
                    'LABEL_PUBLISHED',
                    'LABEL_VERSION',
                    ?,
                    NOW(),
                    ?,
                    JSON_OBJECT(
                        'lifecycle_status',
                        'APPROVED',
                        'is_current_published',
                        'N'
                    ),
                    JSON_OBJECT(
                        'lifecycle_status',
                        'PUBLISHED',
                        'is_current_published',
                        'Y'
                    ),
                    JSON_OBJECT(
                        'helper',
                        'LabelWorkflowService'
                    ),
                    ?,
                    ?
                )
                """,
                "audit_label_publish_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", ""),
                labelVersionId,
                actorUserId,
                labelVersionId,
                dataProvenanceId
        );
    }
}