package com.spectrace.impact.infrastructure;

import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReviewTaskLinkageRepository implements ReviewTaskLinkageRepository {

    private static final String INSERT = """
            INSERT INTO review_task(
              review_task_id, impact_finding_id, product_id, current_label_version_id,
              draft_label_version_id, status, assigned_to_user_id, created_by_user_id,
              created_at, data_provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT = """
            SELECT review_task_id, impact_finding_id, product_id, current_label_version_id,
                   draft_label_version_id, status, assigned_to_user_id, created_by_user_id,
                   created_at, data_provenance_id
            FROM review_task
            WHERE impact_finding_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReviewTaskLinkageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReviewTaskLinkage saveOrGetExisting(ReviewTaskLinkage linkage) {
        Optional<ReviewTaskLinkage> existing = findByFindingId(linkage.impactFindingId());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            int updated = jdbcTemplate.update(
                    INSERT,
                    linkage.reviewTaskId(),
                    linkage.impactFindingId(),
                    linkage.productId(),
                    linkage.currentLabelVersionId(),
                    linkage.draftLabelVersionId(),
                    linkage.status().name(),
                    linkage.assignedToUserId(),
                    linkage.createdByUserId(),
                    ImpactJdbcValueMapping.writeUtcDateTime(linkage.createdAt()),
                    linkage.dataProvenanceId()
            );
            if (updated != 1) {
                throw new IllegalStateException("Expected one review task linkage row to be inserted");
            }
            return linkage;
        } catch (DuplicateKeyException duplicate) {
            return findByFindingId(linkage.impactFindingId()).orElseThrow(() -> duplicate);
        }
    }

    @Override
    public Optional<ReviewTaskLinkage> findByFindingId(String impactFindingId) {
        List<ReviewTaskLinkage> rows = jdbcTemplate.query(SELECT, this::mapReviewTaskLinkage, impactFindingId);
        return rows.stream().findFirst();
    }

    private ReviewTaskLinkage mapReviewTaskLinkage(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ReviewTaskLinkage(
                resultSet.getString("review_task_id"),
                resultSet.getString("impact_finding_id"),
                resultSet.getString("product_id"),
                resultSet.getString("current_label_version_id"),
                resultSet.getString("draft_label_version_id"),
                ReviewTaskStatus.fromDatabase(resultSet.getString("status")),
                resultSet.getString("assigned_to_user_id"),
                resultSet.getString("created_by_user_id"),
                ImpactJdbcValueMapping.readUtcDateTime(resultSet, "created_at"),
                resultSet.getString("data_provenance_id")
        );
    }
}
