package com.spectrace.workflow.infrastructure;

import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcReviewTaskDraftBinding implements ReviewTaskDraftBinding {

    private final JdbcTemplate jdbc;

    public JdbcReviewTaskDraftBinding(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindOpenTaskToDraft(
            String productId,
            String jurisdictionCode,
            String labelVersionId
    ) {
        List<String> taskIds = jdbc.query(
                """
                SELECT rt.review_task_id
                FROM product p
                JOIN label_version draft
                  ON draft.label_version_id = ?
                 AND draft.product_id = p.product_id
                 AND draft.jurisdiction_code = ?
                JOIN label_version current_label
                  ON current_label.label_version_id =
                     p.current_published_label_version_id
                JOIN review_task rt
                  ON rt.product_id = p.product_id
                 AND rt.current_label_version_id =
                     current_label.label_version_id
                WHERE p.product_id = ?
                  AND rt.status = 'OPEN'
                  AND rt.resolved_at IS NULL
                  AND rt.draft_label_version_id IS NULL
                  AND rt.target_label_version_id IS NULL
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getString("review_task_id"),
                labelVersionId,
                jurisdictionCode,
                productId
        );

        if (taskIds.isEmpty()) {
            return;
        }
        if (taskIds.size() != 1) {
            throw new IllegalStateException(
                    "More than one open ReviewTask matches the new label draft"
            );
        }

        int updated = jdbc.update(
                """
                UPDATE review_task
                SET draft_label_version_id = ?,
                    target_label_version_id = ?
                WHERE review_task_id = ?
                  AND status = 'OPEN'
                  AND resolved_at IS NULL
                  AND draft_label_version_id IS NULL
                  AND target_label_version_id IS NULL
                """,
                labelVersionId,
                labelVersionId,
                taskIds.getFirst()
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "ReviewTask draft binding changed during label creation"
            );
        }
    }
}
