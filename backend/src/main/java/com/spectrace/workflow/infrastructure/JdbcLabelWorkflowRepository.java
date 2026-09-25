package com.spectrace.workflow.infrastructure;

import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcLabelWorkflowRepository
        implements LabelWorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabelWorkflowRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findCreatorUserId(
            String labelVersionId
    ) {
        return jdbcTemplate.query(
                """
                SELECT created_by_user_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                rs -> rs.next()
                        ? Optional.ofNullable(
                                rs.getString(
                                        "created_by_user_id"
                                )
                        )
                        : Optional.empty(),
                labelVersionId
        );
    }

    @Override
    public Optional<LabelWorkflowVersion> findVersion(
            String labelVersionId
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    lv.label_version_id,
                    lv.lifecycle_status,
                    CASE
                        WHEN lv.lifecycle_status = 'PUBLISHED'
                            THEN lv.is_current_published = 'Y'
                        WHEN lv.lifecycle_status IN (
                            'DRAFT',
                            'PENDING_REVIEW',
                            'APPROVED'
                        )
                            THEN lv.formula_version_id =
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
                        ELSE FALSE
                    END AS is_current
                FROM label_version lv
                JOIN product p
                  ON p.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                """,
                rs -> rs.next()
                        ? Optional.of(
                                new LabelWorkflowVersion(
                                        rs.getString(
                                                "label_version_id"
                                        ),
                                        rs.getString(
                                                "lifecycle_status"
                                        ),
                                        rs.getBoolean(
                                                "is_current"
                                        )
                                )
                        )
                        : Optional.empty(),
                labelVersionId
        );
    }

    @Override
    public void submitForReview(
            String labelVersionId,
            String actorUserId
    ) {
        try {
            jdbcTemplate.update(
                    "CALL sp_submit_label_for_review(?, ?)",
                    labelVersionId,
                    actorUserId
            );
        } catch (DataAccessException error) {
            throw mapVersionConflict(error);
        }
    }

    @Override
    public void recordDecision(
            String labelVersionId,
            String decision,
            String actorUserId,
            String comments
    ) {
        try {
            jdbcTemplate.update(
                    "CALL sp_record_label_decision(?, ?, ?, ?)",
                    labelVersionId,
                    actorUserId,
                    decision,
                    comments
            );
        } catch (DataAccessException error) {
            throw mapVersionConflict(error);
        }
    }

    private RuntimeException mapVersionConflict(
            DataAccessException error
    ) {
        Throwable cause = error.getMostSpecificCause();

        String message = cause == null
                ? error.getMessage()
                : cause.getMessage();

        if (message != null
                && message.contains(
                        "LABEL_VERSION_CONFLICT"
                )) {
            return new LabelVersionConflictException(
                    "Label version is stale or historical"
            );
        }

        return error;
    }
}