package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Catalog-owned adapter for the immutable formula/specification composition contract. */
@Repository
public class JdbcFormulaCompositionAdapter implements FormulaCompositionPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFormulaCompositionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FormulaCompositionSnapshot> findById(String formulaVersionId) {
        List<FormulaRow> formulas = jdbcTemplate.query(
                """
                SELECT formula_version_id, product_id, is_current_released
                FROM formula_version
                WHERE formula_version_id = ?
                """,
                (rs, rowNum) -> new FormulaRow(
                        rs.getString("formula_version_id"),
                        rs.getString("product_id"),
                        "Y".equals(rs.getString("is_current_released"))
                ),
                formulaVersionId
        );
        if (formulas.isEmpty()) {
            return Optional.empty();
        }

        FormulaRow formula = formulas.getFirst();
        List<ItemRow> itemRows = jdbcTemplate.query(
                """
                SELECT formula_item_id, supplier_material_id, specification_version_id
                FROM formula_item
                WHERE formula_version_id = ?
                ORDER BY sequence_no, formula_item_id
                """,
                (rs, rowNum) -> new ItemRow(
                        rs.getString("formula_item_id"),
                        rs.getString("supplier_material_id"),
                        rs.getString("specification_version_id")
                ),
                formulaVersionId
        );

        List<Item> items = new ArrayList<>();
        for (ItemRow item : itemRows) {
            List<Component> components = jdbcTemplate.query(
                    """
                    SELECT spec_component_id, ingredient_id, raw_phrase, match_rule, match_status
                    FROM spec_component
                    WHERE specification_version_id = ?
                    ORDER BY sequence_no, spec_component_id
                    """,
                    (rs, rowNum) -> new Component(
                            rs.getString("spec_component_id"),
                            rs.getString("ingredient_id"),
                            rs.getString("raw_phrase"),
                            rs.getString("match_rule"),
                            FormulaCompositionSnapshot.MatchStatus.valueOf(
                                    rs.getString("match_status"))
                    ),
                    item.specificationVersionId()
            );
            items.add(new Item(
                    item.formulaItemId(), item.supplierMaterialId(),
                    item.specificationVersionId(), components));
        }

        return Optional.of(new FormulaCompositionSnapshot(
                formula.productId(), formula.formulaVersionId(), formula.currentReleased(), items));
    }

    private record FormulaRow(String formulaVersionId, String productId, boolean currentReleased) {
    }

    private record ItemRow(String formulaItemId, String supplierMaterialId, String specificationVersionId) {
    }
}
