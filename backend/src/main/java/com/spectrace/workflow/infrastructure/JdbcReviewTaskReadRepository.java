package com.spectrace.workflow.infrastructure;

import com.spectrace.workflow.application.ReviewTaskView;
import com.spectrace.workflow.application.port.ReviewTaskReadRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class JdbcReviewTaskReadRepository implements ReviewTaskReadRepository {
    private static final String SELECT = """
            SELECT review_task_id, product_id, current_label_version_id,
                   draft_label_version_id, target_label_version_id, status, decision, resolved_at
            FROM review_task
            """;
    private static final String ORDER = " ORDER BY created_at DESC, review_task_id ASC LIMIT ? OFFSET ?";
    private static final RowMapper<ReviewTaskView> ROW = (rs, rowNum) -> new ReviewTaskView(
            rs.getString("review_task_id"), rs.getString("product_id"),
            rs.getString("current_label_version_id"), rs.getString("draft_label_version_id"),
            rs.getString("target_label_version_id"), rs.getString("status"), rs.getString("decision"),
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toLocalDateTime());
    private final JdbcTemplate jdbc;

    public JdbcReviewTaskReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<ReviewTaskView> list(int limit, int offset, String status) {
        return status == null
                ? jdbc.query(SELECT + ORDER, ROW, limit, offset)
                : jdbc.query(SELECT + " WHERE status = ?" + ORDER, ROW, status, limit, offset);
    }
}
