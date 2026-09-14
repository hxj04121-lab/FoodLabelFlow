package com.spectrace.label.infrastructure;

import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Owner-side adapter for the immutable label snapshot used by validation. */
@Repository
public class JdbcLabelSnapshotAdapter implements LabelSnapshotPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLabelSnapshotAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LabelValidationSnapshot> findValidationSnapshot(String labelVersionId) {
        List<LabelRow> labels = jdbcTemplate.query(
                """
                SELECT lv.label_version_id, p.current_published_label_version_id,
                       lv.rule_set_version_id, lv.jurisdiction_code,
                       lv.raw_ingredient_text, lv.data_provenance_id
                FROM label_version lv
                JOIN product p ON p.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                """,
                (rs, rowNum) -> new LabelRow(
                        rs.getString("label_version_id"),
                        rs.getString("current_published_label_version_id"),
                        rs.getString("rule_set_version_id"),
                        rs.getString("jurisdiction_code"),
                        rs.getString("raw_ingredient_text"),
                        rs.getString("data_provenance_id")
                ),
                labelVersionId
        );
        if (labels.isEmpty()) {
            return Optional.empty();
        }

        LabelRow label = labels.getFirst();
        List<String> declaredAllergenIds = jdbcTemplate.queryForList(
                """
                SELECT allergen_id
                FROM label_allergen_declaration
                WHERE label_version_id = ? AND declaration_type = 'CONTAINS'
                ORDER BY allergen_id
                """,
                String.class,
                labelVersionId
        );
        return Optional.of(new LabelValidationSnapshot(
                label.labelVersionId(),
                label.currentLabelVersionId(),
                label.ruleSetVersionId(),
                label.jurisdictionCode(),
                label.rawIngredientText(),
                declaredAllergenIds,
                label.dataProvenanceId()
        ));
    }

    private record LabelRow(
            String labelVersionId,
            String currentLabelVersionId,
            String ruleSetVersionId,
            String jurisdictionCode,
            String rawIngredientText,
            String dataProvenanceId
    ) {
    }
}
