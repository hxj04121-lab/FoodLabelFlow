package com.spectrace.label.infrastructure;

import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.label.application.port.LabelValidationSnapshot.AllergenDeclaration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Label-owned adapter for the immutable validation snapshot contract. */
@Repository
public class JdbcLabelSnapshotAdapter implements LabelSnapshotPort {

    private final JdbcTemplate jdbcTemplate;
    private final FormulaCompositionPort formulas;

    public JdbcLabelSnapshotAdapter(JdbcTemplate jdbcTemplate, FormulaCompositionPort formulas) {
        this.jdbcTemplate = jdbcTemplate;
        this.formulas = formulas;
    }

    @Override
    public Optional<LabelValidationSnapshot> findById(String labelVersionId) {
        List<LabelRow> labels = jdbcTemplate.query(
                """
                SELECT label_version_id, product_id, formula_version_id, rule_set_version_id,
                       jurisdiction_code, raw_ingredient_text, lifecycle_status,
                       is_current_published, data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                (rs, rowNum) -> new LabelRow(
                        rs.getString("label_version_id"),
                        rs.getString("product_id"),
                        rs.getString("formula_version_id"),
                        rs.getString("rule_set_version_id"),
                        rs.getString("jurisdiction_code"),
                        rs.getString("raw_ingredient_text"),
                        rs.getString("lifecycle_status"),
                        "Y".equals(rs.getString("is_current_published")),
                        rs.getString("data_provenance_id")
                ),
                labelVersionId
        );
        if (labels.isEmpty()) {
            return Optional.empty();
        }

        LabelRow label = labels.getFirst();
        boolean currentFormula = formulas.findById(label.formulaVersionId())
                .map(formula -> formula.isCurrentReleased()
                        && formula.productId().equals(label.productId()))
                .orElse(false);
        boolean current = currentFormula && switch (label.lifecycleStatus()) {
            case "DRAFT", "PENDING_REVIEW", "APPROVED" -> true;
            case "PUBLISHED" -> label.currentPublished();
            default -> false;
        };

        List<AllergenDeclaration> declarations = jdbcTemplate.query(
                """
                SELECT allergen_id, declaration_type, declaration_source, display_text
                FROM label_allergen_declaration
                WHERE label_version_id = ?
                ORDER BY declaration_type, allergen_id
                """,
                (rs, rowNum) -> new AllergenDeclaration(
                        rs.getString("allergen_id"),
                        rs.getString("declaration_type"),
                        rs.getString("declaration_source"),
                        rs.getString("display_text")
                ),
                labelVersionId
        );

        return Optional.of(new LabelValidationSnapshot(
                label.labelVersionId(), label.productId(), label.formulaVersionId(),
                label.ruleSetVersionId(), label.jurisdictionCode(), label.rawIngredientText(),
                current, label.dataProvenanceId(), declarations));
    }

    private record LabelRow(
            String labelVersionId,
            String productId,
            String formulaVersionId,
            String ruleSetVersionId,
            String jurisdictionCode,
            String rawIngredientText,
            String lifecycleStatus,
            boolean currentPublished,
            String dataProvenanceId
    ) {
    }
}
