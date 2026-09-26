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
                SET status = 'IN_REVIEW'
                WHERE draft_label_version_id = ?
                  AND status = 'OPEN'
                """,
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
        return jdbc.query(
                """
                SELECT
                    lv.label_version_id,
                    lv.lifecycle_status,
                    lv.created_by_user_id,
                    lv.data_provenance_id,
                    rt.review_task_id,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM label_version newer
                            WHERE newer.product_id = lv.product_id
                              AND newer.jurisdiction_code = lv.jurisdiction_code
                              AND newer.version_number > lv.version_number
                        ) THEN 0
                        ELSE 1
                    END AS is_current
                FROM label_version lv
                LEFT JOIN review_task rt
                  ON rt.draft_label_version_id = lv.label_version_id
                 AND rt.status = 'IN_REVIEW'
                WHERE lv.label_version_id = ?
                FOR UPDATE
                """,
                rs -> rs.next()
                        ? Optional.of(
                                new DecisionTarget(
                                        rs.getString("label_version_id"),
                                        rs.getString("lifecycle_status"),
                                        rs.getString("created_by_user_id"),
                                        rs.getString("review_task_id"),
                                        rs.getBoolean("is_current"),
                                        rs.getString("data_provenance_id")
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
