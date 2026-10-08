package com.spectrace.workflow.infrastructure;

import com.spectrace.label.application.InvalidLabelDraftRequestException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    private record Task(String id, String draftId, String targetId, String status, boolean matchesFindingFormula) {}
}
