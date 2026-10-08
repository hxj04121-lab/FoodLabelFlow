package com.spectrace.workflow.infrastructure;

import com.spectrace.workflow.application.port.LabelReviewCommandRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLabelReviewCommandRepository
        implements LabelReviewCommandRepository {

    private final JdbcTemplate jdbc;

    public JdbcLabelReviewCommandRepository(
            JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReviewTarget> lockForReview(
            String labelVersionId
    ) {
        return jdbc.query(
                """
                SELECT
                    lv.label_version_id,
                    lv.rule_set_version_id,
                    lv.lifecycle_status,
                    lv.data_provenance_id,
                    CASE
                        WHEN lv.formula_version_id =
                             p.current_formula_version_id
                        THEN 1
                        ELSE 0
                    END AS is_current_formula,
                    CASE
                        WHEN lv.lifecycle_status IN (
                            'SUPERSEDED',
                            'REJECTED'
                        ) THEN 0
                        WHEN EXISTS (
                            SELECT 1
                            FROM label_version newer
                            WHERE newer.product_id =
                                  lv.product_id
                              AND newer.jurisdiction_code =
                                  lv.jurisdiction_code
                              AND newer.version_number >
                                  lv.version_number
                        ) THEN 0
                        ELSE 1
                    END AS is_current
                FROM label_version lv
                JOIN product p
                  ON p.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                FOR UPDATE
                """,
                rs -> rs.next()
                        ? Optional.of(
                                new ReviewTarget(
                                        rs.getString(
                                                "label_version_id"
                                        ),
                                        rs.getString(
                                                "rule_set_version_id"
                                        ),
                                        rs.getString(
                                                "lifecycle_status"
                                        ),
                                        rs.getBoolean(
                                                "is_current"
                                        ),
                                        rs.getBoolean(
                                                "is_current_formula"
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
    public boolean hasPassingValidation(
            String labelVersionId,
            String ruleSetVersionId
    ) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM validation_run vr
                WHERE vr.label_version_id = ?
                  AND vr.rule_set_version_id = ?
                  AND vr.status = 'PASSED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM validation_run newer
                      WHERE newer.label_version_id =
                            vr.label_version_id
                        AND newer.rule_set_version_id =
                            vr.rule_set_version_id
                        AND (
                            newer.ran_at > vr.ran_at
                            OR (
                                newer.ran_at = vr.ran_at
                                AND newer.validation_run_id >
                                    vr.validation_run_id
                            )
                        )
                  )
                """,
                Integer.class,
                labelVersionId,
                ruleSetVersionId
        );

        return count != null && count > 0;
    }

    @Override
    public int markPendingReview(
            String labelVersionId
    ) {
        return jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'PENDING_REVIEW'
                WHERE label_version_id = ?
                  AND lifecycle_status = 'DRAFT'
                """,
                labelVersionId
        );
    }

    @Override
    public void markReviewTaskInReview(
            String labelVersionId
    ) {
        jdbc.update(
                """
                UPDATE review_task
                SET status = 'IN_REVIEW',
                    target_label_version_id = ?
                WHERE (draft_label_version_id = ?
                       OR target_label_version_id = ?)
                  AND status = 'OPEN'
                  AND resolved_at IS NULL
                  AND (target_label_version_id IS NULL
                       OR target_label_version_id = ?)
                """,
                labelVersionId,
                labelVersionId,
                labelVersionId,
                labelVersionId
        );
    }

    @Override
    public void createSubmitAudit(
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
                    'LABEL_PENDING_REVIEW',
                    'LABEL_VERSION',
                    ?,
                    NOW(),
                    ?,
                    JSON_OBJECT(
                        'lifecycle_status',
                        'DRAFT'
                    ),
                    JSON_OBJECT(
                        'lifecycle_status',
                        'PENDING_REVIEW'
                    ),
                    JSON_OBJECT(
                        'helper',
                        'LabelWorkflowService'
                    ),
                    ?,
                    ?
                )
                """,
                "audit_label_submit_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", ""),
                labelVersionId,
                actorUserId,
                labelVersionId,
                dataProvenanceId
        );
    }

    @Override
    public Optional<DecisionTarget> lockForDecision(
            String labelVersionId
    ) {
        jdbc.query(
            """
            SELECT p.product_id
            FROM product p
            JOIN label_version lv
              ON lv.product_id = p.product_id
            WHERE lv.label_version_id = ?
            FOR UPDATE
            """,
            rs -> rs.next()
                    ? rs.getString("product_id")
                    : null,
            labelVersionId
        );

        return jdbc.query(
            """
            SELECT
                lv.label_version_id,
                lv.lifecycle_status,
                lv.created_by_user_id,
                lv.rule_set_version_id,
                lv.data_provenance_id,
                rt.review_task_id,
                CASE
                    WHEN lv.formula_version_id =
                         p.current_formula_version_id
                    THEN 1
                    ELSE 0
                END AS is_current_formula,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM label_version newer
                        WHERE newer.product_id = lv.product_id
                          AND newer.jurisdiction_code =
                              lv.jurisdiction_code
                          AND newer.version_number >
                              lv.version_number
                    ) THEN 0
                    ELSE 1
                END AS is_current
            FROM label_version lv
            JOIN product p
              ON p.product_id = lv.product_id
            LEFT JOIN review_task rt
              ON rt.target_label_version_id =
                 lv.label_version_id
             AND rt.status = 'IN_REVIEW'
             AND rt.resolved_at IS NULL
            WHERE lv.label_version_id = ?
            FOR UPDATE
            """,
            rs -> rs.next()
                    ? Optional.of(
                            new DecisionTarget(
                                    rs.getString(
                                            "label_version_id"
                                    ),
                                    rs.getString(
                                            "lifecycle_status"
                                    ),
                                    rs.getString(
                                            "created_by_user_id"
                                    ),
                                    rs.getString(
                                            "review_task_id"
                                    ),
                                    rs.getBoolean(
                                            "is_current"
                                    ),
                                    rs.getBoolean(
                                            "is_current_formula"
                                    ),
                                    rs.getString(
                                            "rule_set_version_id"
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
    public int updateDecisionState(
            String labelVersionId,
            String newStatus
    ) {
        return jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = ?
                WHERE label_version_id = ?
                  AND lifecycle_status = 'PENDING_REVIEW'
                """,
                newStatus,
                labelVersionId
        );
    }
    @Override
    public int updateReviewTaskStatus(
            String reviewTaskId,
            String labelVersionId,
            String newStatus,
            String decision,
            String resolverUserId,
            boolean resolved
    ) {
        return jdbc.update(
                """
                UPDATE review_task
                SET status = ?,
                    target_label_version_id = ?,
                    decision = ?,
                    resolved_by_user_id = ?,
                    resolved_at = CASE WHEN ? THEN NOW() ELSE NULL END
                WHERE review_task_id = ?
                  AND status = 'IN_REVIEW'
                  AND target_label_version_id = ?
                  AND resolved_at IS NULL
                """,
                newStatus,
                labelVersionId,
                decision,
                resolved ? resolverUserId : null,
                resolved,
                reviewTaskId,
                labelVersionId
        );
    }

    @Override
    public Optional<PublicationTarget> lockForPublication(
            String reviewTaskId
    ) {
        String productId = jdbc.query(
                """
                SELECT lv.product_id
                FROM review_task rt
                JOIN label_version lv
                  ON lv.label_version_id = rt.target_label_version_id
                WHERE rt.review_task_id = ?
                """,
                rs -> rs.next() ? rs.getString("product_id") : null,
                reviewTaskId
        );
        if (productId == null) {
            return Optional.empty();
        }

        // Serialize publication with draft creation and formula-pointer changes.
        jdbc.query(
                "SELECT product_id FROM product WHERE product_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString("product_id") : null,
                productId
        );

        return jdbc.query(
                """
                SELECT
                    rt.review_task_id,
                    rt.target_label_version_id,
                    rt.decision,
                    rt.status AS task_status,
                    rt.resolved_at,
                    lv.lifecycle_status,
                    lv.product_id,
                    lv.data_provenance_id,
                    CASE
                        WHEN lv.formula_version_id = p.current_formula_version_id
                         AND fv.lifecycle_status = 'RELEASED'
                         AND fv.is_current_released = 'Y'
                        THEN 1 ELSE 0
                    END AS is_current_formula,
                    CASE WHEN lv.version_number = (
                        SELECT MAX(latest.version_number)
                        FROM label_version latest
                        WHERE latest.product_id = lv.product_id
                          AND latest.jurisdiction_code = lv.jurisdiction_code
                    ) THEN 1 ELSE 0 END AS is_latest_label_version,
                    EXISTS (
                        SELECT 1
                        FROM approval_record ar
                        WHERE ar.review_task_id = rt.review_task_id
                          AND ar.label_version_id = rt.target_label_version_id
                          AND ar.decision = 'APPROVE'
                    ) AS has_approve_record,
                    EXISTS (
                        SELECT 1
                        FROM validation_run vr
                        WHERE vr.label_version_id = lv.label_version_id
                          AND vr.rule_set_version_id = lv.rule_set_version_id
                          AND vr.status = 'PASSED'
                          AND NOT EXISTS (
                              SELECT 1 FROM validation_run newer
                              WHERE newer.label_version_id = vr.label_version_id
                                AND newer.rule_set_version_id = vr.rule_set_version_id
                                AND (newer.ran_at > vr.ran_at
                                  OR (newer.ran_at = vr.ran_at
                                    AND newer.validation_run_id > vr.validation_run_id))
                          )
                    ) AS has_passing_validation
                FROM review_task rt
                JOIN label_version lv
                  ON lv.label_version_id = rt.target_label_version_id
                JOIN product p
                  ON p.product_id = lv.product_id
                JOIN formula_version fv
                  ON fv.formula_version_id = lv.formula_version_id
                WHERE rt.review_task_id = ?
                FOR UPDATE
                """,
                rs -> rs.next()
                        ? Optional.of(new PublicationTarget(
                                rs.getString("review_task_id"),
                                rs.getString("target_label_version_id"),
                                rs.getString("decision"),
                                rs.getString("task_status"),
                                rs.getTimestamp("resolved_at") == null
                                        ? null
                                        : rs.getTimestamp("resolved_at").toLocalDateTime(),
                                rs.getString("lifecycle_status"),
                                rs.getString("product_id"),
                                rs.getString("data_provenance_id"),
                                rs.getBoolean("is_current_formula"),
                                rs.getBoolean("is_latest_label_version"),
                                rs.getBoolean("has_approve_record"),
                                rs.getBoolean("has_passing_validation")
                        ))
                        : Optional.empty(),
                reviewTaskId
        );
    }

    @Override
    public int supersedePublishedVersion(String labelVersionId) {
        return jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'SUPERSEDED',
                    is_current_published = 'N'
                WHERE product_id = (
                    SELECT target.product_id
                    FROM (SELECT product_id
                          FROM label_version
                          WHERE label_version_id = ?) target
                )
                  AND jurisdiction_code = (
                    SELECT target.jurisdiction_code
                    FROM (SELECT jurisdiction_code
                          FROM label_version
                          WHERE label_version_id = ?) target
                )
                  AND label_version_id <> ?
                  AND lifecycle_status = 'PUBLISHED'
                  AND is_current_published = 'Y'
                """,
                labelVersionId,
                labelVersionId,
                labelVersionId
        );
    }

    @Override
    public int publishApprovedVersion(String labelVersionId) {
        return jdbc.update(
                """
                UPDATE label_version
                SET lifecycle_status = 'PUBLISHED',
                    is_current_published = 'Y'
                WHERE label_version_id = ?
                  AND lifecycle_status = 'APPROVED'
                  AND is_current_published = 'N'
                """,
                labelVersionId
        );
    }

    @Override
    public int updateCurrentPublishedVersion(
            String productId,
            String labelVersionId
    ) {
        return jdbc.update(
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
                ) VALUES (?, ?, ?, NOW(), 'DEMO_RELEASE', ?)
                """,
                "publication_" + labelVersionId,
                labelVersionId,
                actorUserId,
                dataProvenanceId
        );
    }

    @Override
    public void createPublicationAudit(
            String labelVersionId,
            String reviewTaskId,
            String actorUserId,
            String dataProvenanceId
    ) {
        jdbc.update(
                """
                INSERT INTO audit_event (
                    audit_event_id, event_type, entity_type, entity_id,
                    event_at, actor_user_id, before_value, after_value,
                    event_payload, correlation_id, data_provenance_id
                ) VALUES (
                    ?, 'LABEL_PUBLISHED', 'LABEL_VERSION', ?, NOW(), ?,
                    JSON_OBJECT('lifecycle_status', 'APPROVED',
                                'is_current_published', 'N'),
                    JSON_OBJECT('lifecycle_status', 'PUBLISHED',
                                'is_current_published', 'Y'),
                    JSON_OBJECT('review_task_id', ?,
                                'helper', 'LabelReviewService'),
                    ?, ?
                )
                """,
                "audit_label_publish_" + UUID.randomUUID()
                        .toString().replace("-", ""),
                labelVersionId,
                actorUserId,
                reviewTaskId,
                labelVersionId,
                dataProvenanceId
        );
    }

    @Override
    public int resolvePublishedReviewTask(
            String reviewTaskId,
            String labelVersionId,
            String resolverUserId
    ) {
        return jdbc.update(
                """
                UPDATE review_task
                SET status = 'CLOSED',
                    resolved_by_user_id = ?,
                    resolved_at = NOW()
                WHERE review_task_id = ?
                  AND target_label_version_id = ?
                  AND decision = 'APPROVE'
                  AND status = 'IN_REVIEW'
                  AND resolved_at IS NULL
                """,
                resolverUserId,
                reviewTaskId,
                labelVersionId
        );
    }

    @Override
    public void createApprovalRecord(
            String labelVersionId,
            String reviewTaskId,
            String decision,
            String actorUserId,
            String comments,
            String dataProvenanceId
    ) {
        jdbc.update(
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
                VALUES (?, ?, ?, ?, ?, NOW(), ?, ?)
                """,
                "approval_" + UUID.randomUUID()
                        .toString().replace("-", ""),
                labelVersionId,
                reviewTaskId,
                decision,
                actorUserId,
                comments,
                dataProvenanceId
        );
    }

    @Override
    public void createDecisionAudit(
            String labelVersionId,
            String decision,
            String beforeStatus,
            String afterStatus,
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
                    'LABEL_DECISION_RECORDED',
                    'LABEL_VERSION',
                    ?,
                    NOW(),
                    ?,
                    JSON_OBJECT(
                        'lifecycle_status', ?
                    ),
                    JSON_OBJECT(
                        'lifecycle_status', ?
                    ),
                    JSON_OBJECT(
                        'decision', ?,
                        'helper', 'LabelReviewService'
                    ),
                    ?,
                    ?
                )
                """,
                "audit_label_decision_"
                        + UUID.randomUUID()
                        .toString().replace("-", ""),
                labelVersionId,
                actorUserId,
                beforeStatus,
                afterStatus,
                decision,
                labelVersionId,
                dataProvenanceId
        );
    }}
