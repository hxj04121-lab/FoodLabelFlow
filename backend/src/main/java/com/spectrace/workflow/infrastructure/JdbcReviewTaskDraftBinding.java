package com.spectrace.workflow.infrastructure;

import com.spectrace.label.application.InvalidLabelDraftRequestException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.spectrace.workflow.application.ReviewTaskNotFoundException;
import java.util.UUID;

@Repository
public class JdbcReviewTaskDraftBinding implements ReviewTaskDraftBinding {
    private final JdbcTemplate jdbc;

    public JdbcReviewTaskDraftBinding(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireFirstDraftAvailable(String productId, String jurisdictionCode, String expectedReviewTaskId) {
        String locked = jdbc.query("SELECT product_id FROM product WHERE product_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString("product_id") : null, productId);
        if (locked == null) {
            throw new InvalidLabelDraftRequestException("Unknown product: " + productId);
        }
        var tasks = jdbc.query("""
                SELECT rt.review_task_id, rt.draft_label_version_id, rt.target_label_version_id, rt.status,
                       CASE WHEN finding.product_id = p.product_id
                              AND finding.current_label_version_id = current_label.label_version_id
                              AND finding.current_formula_version_id = current_label.formula_version_id
                              AND COALESCE(finding.proposed_formula_version_id, finding.current_formula_version_id)
                                  = p.current_formula_version_id
                            THEN 1 ELSE 0 END AS matches_finding_formula
                FROM product p
                JOIN label_version current_label
                  ON current_label.label_version_id = p.current_published_label_version_id
                 AND current_label.product_id = p.product_id
                 AND current_label.jurisdiction_code = ?
                JOIN review_task rt
                  ON rt.product_id = p.product_id
                 AND rt.current_label_version_id = current_label.label_version_id
                JOIN impact_finding finding ON finding.impact_finding_id = rt.impact_finding_id
                WHERE p.product_id = ?
                  AND rt.status IN ('OPEN', 'IN_REVIEW')
                  AND rt.resolved_at IS NULL
                FOR UPDATE
                """, (rs, row) -> new Task(rs.getString("review_task_id"), rs.getString("draft_label_version_id"),
                rs.getString("target_label_version_id"), rs.getString("status"), rs.getBoolean("matches_finding_formula")), jurisdictionCode, productId);
        if (tasks.size() > 1) {
            throw new LabelVersionConflictException("More than one unresolved ReviewTask matches the product and jurisdiction");
        }
        if (tasks.isEmpty()) {
            if (expectedReviewTaskId != null) {
                throw new LabelVersionConflictException("ReviewTask does not match the current product label and jurisdiction");
            }
            return;
        }
        var task = tasks.getFirst();
        if (expectedReviewTaskId != null && !expectedReviewTaskId.equals(task.id())) {
            throw new LabelVersionConflictException("ReviewTask does not match the current product label and jurisdiction");
        }
        if (!task.matchesFindingFormula()) {
            throw new LabelVersionConflictException("ReviewTask impact finding does not match the published label and current formula");
        }
        if (!"OPEN".equals(task.status()) || task.draftId() != null || task.targetId() != null) {
            throw new LabelVersionConflictException("ReviewTask already has a draft or target; existing label declarations cannot be replaced");
        }
    }

    @Override
    public void bindOpenTaskToDraft(String productId, String jurisdictionCode, String labelVersionId, String expectedReviewTaskId) {
        var taskIds = jdbc.query("""
                SELECT rt.review_task_id
                FROM product p
                JOIN label_version draft
                  ON draft.label_version_id = ?
                 AND draft.product_id = p.product_id
                 AND draft.jurisdiction_code = ?
                JOIN label_version current_label
                  ON current_label.label_version_id = p.current_published_label_version_id
                 AND current_label.product_id = p.product_id
                 AND current_label.jurisdiction_code = draft.jurisdiction_code
                JOIN review_task rt
                  ON rt.product_id = p.product_id
                 AND rt.current_label_version_id = current_label.label_version_id
                WHERE p.product_id = ?
                  AND rt.status = 'OPEN'
                  AND rt.resolved_at IS NULL
                  AND rt.draft_label_version_id IS NULL
                  AND rt.target_label_version_id IS NULL
                FOR UPDATE
                """, (rs, row) -> rs.getString("review_task_id"), labelVersionId, jurisdictionCode, productId);
        if (taskIds.isEmpty() && expectedReviewTaskId == null) return;
        if (taskIds.size() != 1 || (expectedReviewTaskId != null && !expectedReviewTaskId.equals(taskIds.getFirst()))) {
            throw new LabelVersionConflictException("ReviewTask first-draft binding no longer matches the request");
        }
        int updated = jdbc.update("""
                UPDATE review_task
                SET draft_label_version_id = ?, target_label_version_id = ?
                WHERE review_task_id = ?
                  AND status = 'OPEN' AND resolved_at IS NULL
                  AND draft_label_version_id IS NULL AND target_label_version_id IS NULL
                """, labelVersionId, labelVersionId, taskIds.getFirst());
        if (updated != 1) {
            throw new LabelVersionConflictException("ReviewTask draft binding changed during label creation");
        }
    }

    @Override
    public RevisionTarget requireReturnedDraftAvailable(String reviewTaskId, String expectedLabelVersionId) {
        // The lookup can establish a repeatable-read snapshot. All reads after the
        // product lock are locking reads, including the latest-version subquery.
        String productId = jdbc.query("SELECT product_id FROM review_task WHERE review_task_id = ?",
                rs -> rs.next() ? rs.getString("product_id") : null, reviewTaskId);
        if (productId == null) throw new ReviewTaskNotFoundException(reviewTaskId);
        jdbc.query("SELECT product_id FROM product WHERE product_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString("product_id") : null, productId);
        var returned = jdbc.query("""
                SELECT rt.product_id, rt.draft_label_version_id, rt.target_label_version_id,
                       rt.status, rt.decision, rt.resolved_at,
                       EXISTS (SELECT 1 FROM approval_record ar
                               WHERE ar.review_task_id = rt.review_task_id
                                 AND ar.label_version_id = rt.target_label_version_id
                                 AND ar.decision = 'REQUEST_CHANGES' FOR SHARE) AS has_return_record,
                       old_label.jurisdiction_code, old_label.lifecycle_status,
                       old_label.formula_version_id = p.current_formula_version_id AS current_formula,
                       old_label.version_number = (
                           SELECT newer.version_number FROM label_version newer
                           WHERE newer.product_id = p.product_id
                             AND newer.jurisdiction_code = old_label.jurisdiction_code
                           ORDER BY newer.version_number DESC LIMIT 1 FOR SHARE
                       ) AS latest_label,
                       CASE WHEN finding.product_id = p.product_id
                              AND rt.current_label_version_id = p.current_published_label_version_id
                              AND finding.current_label_version_id = current_label.label_version_id
                              AND finding.current_formula_version_id = current_label.formula_version_id
                              AND COALESCE(finding.proposed_formula_version_id, finding.current_formula_version_id)
                                  = p.current_formula_version_id
                              AND current_label.jurisdiction_code = old_label.jurisdiction_code
                            THEN 1 ELSE 0 END AS matches_finding_formula
                FROM review_task rt
                JOIN product p ON p.product_id = rt.product_id
                JOIN impact_finding finding ON finding.impact_finding_id = rt.impact_finding_id
                LEFT JOIN label_version old_label ON old_label.label_version_id = rt.target_label_version_id
                                                    AND old_label.product_id = p.product_id
                LEFT JOIN label_version current_label ON current_label.label_version_id = p.current_published_label_version_id
                                                        AND current_label.product_id = p.product_id
                WHERE rt.review_task_id = ? FOR UPDATE
                """, (rs, row) -> new ReturnedTask(rs.getString("product_id"),
                rs.getString("jurisdiction_code"), rs.getString("draft_label_version_id"),
                rs.getString("target_label_version_id"), rs.getString("status"), rs.getString("decision"),
                rs.getBoolean("has_return_record"),
                rs.getTimestamp("resolved_at") != null, rs.getString("lifecycle_status"),
                rs.getBoolean("current_formula"), rs.getBoolean("latest_label"),
                rs.getBoolean("matches_finding_formula")), reviewTaskId);
        if (returned.isEmpty()) throw new ReviewTaskNotFoundException(reviewTaskId);
        var task = returned.getFirst();
        if (!expectedLabelVersionId.equals(task.targetId())
                || (task.draftId() != null && !expectedLabelVersionId.equals(task.draftId()))) {
            throw new LabelVersionConflictException("ReviewTask no longer targets the expected returned LabelVersion");
        }
        boolean returnedDecision = "REQUEST_CHANGES".equals(task.decision())
                || (task.decision() == null && task.hasReturnRecord());
        if (!"OPEN".equals(task.status()) || task.resolved() || !returnedDecision
                || !"DRAFT".equals(task.lifecycleStatus())) {
            throw new IllegalStateException("Only an unresolved REQUEST_CHANGES task can create a draft revision");
        }
        if (!task.currentFormula() || !task.latestLabel() || !task.matchesFindingFormula()) {
            throw new LabelVersionConflictException("Returned ReviewTask no longer matches the current label and formula");
        }
        return new RevisionTarget(task.productId(), task.jurisdictionCode());
    }

    @Override
    public void bindReturnedTaskToRevision(String reviewTaskId, String expectedLabelVersionId,
                                           String newLabelVersionId, String actorUserId, String dataProvenanceId) {
        var previousBindings = jdbc.query("SELECT draft_label_version_id, decision FROM review_task WHERE review_task_id = ? FOR UPDATE",
                (rs, row) -> new PreviousBinding(rs.getString("draft_label_version_id"), rs.getString("decision")), reviewTaskId);
        if (previousBindings.size() != 1) {
            throw new LabelVersionConflictException("Returned ReviewTask disappeared during revision creation");
        }
        int updated = jdbc.update("""
                UPDATE review_task rt
                JOIN label_version old_label ON old_label.label_version_id = ?
                  AND old_label.product_id = rt.product_id
                JOIN label_version revision ON revision.label_version_id = ?
                  AND revision.product_id = rt.product_id
                  AND revision.jurisdiction_code = old_label.jurisdiction_code
                  AND revision.lifecycle_status = 'DRAFT'
                SET rt.draft_label_version_id = revision.label_version_id,
                    rt.target_label_version_id = revision.label_version_id,
                    rt.status = 'OPEN', rt.decision = NULL,
                    rt.resolved_by_user_id = NULL, rt.resolved_at = NULL
                WHERE rt.review_task_id = ? AND rt.status = 'OPEN' AND rt.resolved_at IS NULL
                  AND (rt.decision = 'REQUEST_CHANGES' OR (rt.decision IS NULL AND EXISTS (
                      SELECT 1 FROM approval_record ar WHERE ar.review_task_id = rt.review_task_id
                        AND ar.label_version_id = old_label.label_version_id AND ar.decision = 'REQUEST_CHANGES'
                  )))
                  AND (rt.draft_label_version_id = ? OR rt.draft_label_version_id IS NULL)
                  AND rt.target_label_version_id = ?
                """, expectedLabelVersionId, newLabelVersionId, reviewTaskId, expectedLabelVersionId, expectedLabelVersionId);
        if (updated != 1) {
            throw new LabelVersionConflictException("Returned ReviewTask binding changed during revision creation");
        }
        jdbc.update("""
                INSERT INTO audit_event (audit_event_id, event_type, entity_type, entity_id,
                    event_at, actor_user_id, before_value, after_value, event_payload,
                    correlation_id, data_provenance_id)
                VALUES (?, 'LABEL_DRAFT_REVISED', 'LABEL_VERSION', ?, NOW(), ?,
                    JSON_OBJECT('draft_label_version_id', ?, 'target_label_version_id', ?, 'decision', ?),
                    JSON_OBJECT('draft_label_version_id', ?, 'target_label_version_id', ?, 'decision', NULL),
                    JSON_OBJECT('reviewTaskId', ?, 'previousLabelVersionId', ?, 'labelVersionId', ?), ?, ?)
                """, "audit_label_revision_" + UUID.randomUUID().toString().replace("-", ""),
                newLabelVersionId, actorUserId, previousBindings.getFirst().draftId(), expectedLabelVersionId,
                previousBindings.getFirst().decision(),
                newLabelVersionId, newLabelVersionId, reviewTaskId, expectedLabelVersionId,
                newLabelVersionId, reviewTaskId, dataProvenanceId);
    }

    private record ReturnedTask(String productId, String jurisdictionCode, String draftId, String targetId,
                                String status, String decision, boolean hasReturnRecord, boolean resolved, String lifecycleStatus,
                                boolean currentFormula, boolean latestLabel, boolean matchesFindingFormula) {}

    private record PreviousBinding(String draftId, String decision) {}

    private record Task(String id, String draftId, String targetId, String status, boolean matchesFindingFormula) {}
}
