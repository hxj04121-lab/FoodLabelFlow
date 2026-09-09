package com.spectrace.workflow.infrastructure;

import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLabelWorkflowRepository implements LabelWorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabelWorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void submitForReview(
            String labelVersionId,
            String actorUserId
    ) {
        jdbcTemplate.update(
                "CALL sp_submit_label_for_review(?, ?)",
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
        jdbcTemplate.update(
                "CALL sp_record_label_decision(?, ?, ?, ?)",
                labelVersionId,
                actorUserId,
                decision,
                comments
        );
    }
}