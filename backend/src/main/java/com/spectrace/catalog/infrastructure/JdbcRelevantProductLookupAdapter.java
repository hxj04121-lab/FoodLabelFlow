package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * Relevant products read from actual formula history: the product's current pointer
 * must name a RELEASED, current FormulaVersion, and only that version's FormulaItems
 * are matched. Superseded releases, drafts and retired versions never match.
 */
@Repository
public class JdbcRelevantProductLookupAdapter implements RelevantProductLookupPort {
    private final JdbcTemplate jdbc;

    public JdbcRelevantProductLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RelevantProduct> findProductsUsingMaterial(String supplierMaterialId) {
        requiredText(supplierMaterialId, "supplierMaterialId");
        var rows = jdbc.queryForList("""
                SELECT p.product_id, fv.formula_version_id, lv.label_version_id,
                       fi.formula_item_id, spec.supplier_material_id AS spec_material_id
                FROM product p
                JOIN formula_version fv
                  ON fv.formula_version_id = p.current_formula_version_id
                 AND fv.product_id = p.product_id
                 AND fv.lifecycle_status = 'RELEASED'
                 AND fv.is_current_released = 'Y'
                JOIN formula_item fi
                  ON fi.formula_version_id = fv.formula_version_id
                 AND fi.supplier_material_id = ?
                JOIN ingredient_specification_version spec
                  ON spec.specification_version_id = fi.specification_version_id
                LEFT JOIN label_version lv
                  ON lv.label_version_id = p.current_published_label_version_id
                 AND lv.product_id = p.product_id
                 AND lv.lifecycle_status = 'PUBLISHED'
                 AND lv.is_current_published = 'Y'
                ORDER BY p.product_id, fi.sequence_no, fi.formula_item_id
                """, supplierMaterialId);

        Map<String, ProductRows> byProduct = new LinkedHashMap<>();
        for (var row : rows) {
            String formulaItemId = (String) row.get("formula_item_id");
            // A spec of another material behind the item is corrupt data, never a match to drop silently.
            if (!supplierMaterialId.equals(row.get("spec_material_id"))) {
                throw new IllegalStateException("Formula item " + formulaItemId
                        + " references a specification of a different supplier material");
            }
            byProduct.computeIfAbsent((String) row.get("product_id"), productId -> new ProductRows(
                            (String) row.get("formula_version_id"), (String) row.get("label_version_id")))
                    .formulaItemIds().add(formulaItemId);
        }
        return byProduct.entrySet().stream()
                .map(entry -> new RelevantProduct(
                        entry.getKey(),
                        entry.getValue().formulaVersionId(),
                        entry.getValue().labelVersionId(),
                        entry.getValue().formulaItemIds()))
                .toList();
    }

    private record ProductRows(String formulaVersionId, String labelVersionId, List<String> formulaItemIds) {
        ProductRows(String formulaVersionId, String labelVersionId) {
            this(formulaVersionId, labelVersionId, new ArrayList<>());
        }
    }
}
