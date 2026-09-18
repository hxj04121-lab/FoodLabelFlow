package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Captures catalog-owned formula/specification data behind the validation input port. */
@Repository
public class JdbcFormulaCompositionAdapter implements FormulaCompositionPort {
    private final JdbcTemplate jdbc;

    public JdbcFormulaCompositionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FormulaCompositionSnapshot> findById(String formulaVersionId) {
        requiredText(formulaVersionId, "formulaVersionId");
        var formulas = jdbc.queryForList("""
                SELECT product_id, formula_version_id, is_current_released
                FROM formula_version
                WHERE formula_version_id = ?
                FOR UPDATE
                """, formulaVersionId);
        if (formulas.isEmpty()) return Optional.empty();

        var formula = formulas.getFirst();
        List<Item> items = jdbc.queryForList("""
                        SELECT formula_item_id, supplier_material_id, specification_version_id
                        FROM formula_item
                        WHERE formula_version_id = ?
                        ORDER BY sequence_no, formula_item_id
                        FOR SHARE
                        """, formulaVersionId).stream()
                .map(item -> new Item(
                        (String) item.get("formula_item_id"),
                        (String) item.get("supplier_material_id"),
                        (String) item.get("specification_version_id"),
                        components((String) item.get("specification_version_id"))))
                .toList();

        return Optional.of(new FormulaCompositionSnapshot(
                (String) formula.get("product_id"),
                (String) formula.get("formula_version_id"),
                "Y".equals(formula.get("is_current_released")),
                items));
    }

    private List<Component> components(String specificationVersionId) {
        return jdbc.queryForList("""
                        SELECT spec_component_id, ingredient_id, raw_phrase, match_rule, match_status
                        FROM spec_component
                        WHERE specification_version_id = ?
                        ORDER BY sequence_no, spec_component_id
                        FOR SHARE
                        """, specificationVersionId).stream()
                .map(component -> new Component(
                        (String) component.get("spec_component_id"),
                        (String) component.get("ingredient_id"),
                        (String) component.get("raw_phrase"),
                        (String) component.get("match_rule"),
                        MatchStatus.valueOf((String) component.get("match_status"))))
                .toList();
    }
}
