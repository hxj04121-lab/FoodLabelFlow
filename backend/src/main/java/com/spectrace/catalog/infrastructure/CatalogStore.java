package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.domain.CatalogCommands.*;
import com.spectrace.catalog.domain.CatalogFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL is confined to catalog-owned tables; identity and audit are accessed through a port. */
@Repository
public class CatalogStore {
    public enum Kind {
        SUPPLIER("supplier", "supplier_id"), MATERIAL("supplier_material", "supplier_material_id"),
        SPECIFICATION("ingredient_specification_version", "specification_version_id"),
        FORMULA("formula_version", "formula_version_id"), PRODUCT("product", "product_id"),
        INGREDIENT("ingredient", "ingredient_id"), PROVENANCE("data_provenance", "provenance_id");
        final String table;
        final String key;
        Kind(String table, String key) { this.table = table; this.key = key; }
    }
    private final JdbcTemplate jdbc;
    public CatalogStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static String id() { return UUID.randomUUID().toString(); }

    public Map<String, Object> get(Kind kind, String id, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM " + kind.table + " WHERE " + kind.key + " = ?"
                + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new CatalogFailure(404, "RESOURCE_NOT_FOUND", kind + " not found");
        return rows.getFirst();
    }
    public List<Map<String, Object>> list(Kind kind, int limit, int offset) {
        return jdbc.queryForList("SELECT * FROM " + kind.table + " ORDER BY " + kind.key + " LIMIT ? OFFSET ?", limit, offset);
    }
    public List<Map<String, Object>> components(String id) {
        return jdbc.queryForList("SELECT * FROM spec_component WHERE specification_version_id=? ORDER BY sequence_no", id);
    }
    public List<Map<String, Object>> formulaItems(String id) {
        return jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY sequence_no", id);
    }
    public List<Map<String, Object>> versions(String productId) {
        return jdbc.queryForList("SELECT * FROM formula_version WHERE product_id=? ORDER BY version_number", productId);
    }
    public String supplier(Supplier value) {
        String id = id();
        jdbc.update("INSERT INTO supplier(supplier_id,supplier_code,supplier_name,data_provenance_id) VALUES (?,?,?,?)",
                id, value.code(), value.name(), value.provenanceId());
        return id;
    }
    public String material(Material value) {
        String id = id();
        jdbc.update("""
                INSERT INTO supplier_material(supplier_material_id,supplier_id,ingredient_id,material_code,
                  material_name,material_description,data_provenance_id) VALUES (?,?,?,?,?,?,?)
                """, id, value.supplierId(), value.ingredientId(), value.code(), value.name(), value.description(), value.provenanceId());
        return id;
    }
    /** Caller holds the material row lock until transaction completion. */
    public String specification(Specification value, String actor) {
        var previous = jdbc.queryForList("SELECT version_number FROM ingredient_specification_version WHERE supplier_material_id=? ORDER BY version_number DESC LIMIT 1 FOR UPDATE",
                Integer.class, value.materialId());
        int version = previous.isEmpty() ? 1 : previous.getFirst() + 1;
        String id = id();
        jdbc.update("""
                INSERT INTO ingredient_specification_version(specification_version_id,supplier_material_id,
                  version_number,lifecycle_status,effective_date,created_by_user_id,data_provenance_id)
                VALUES (?,?,?,'DRAFT',?,?,?)
                """, id, value.materialId(), version, value.effectiveDate(), actor, value.provenanceId());
        for (int i = 0; i < value.components().size(); i++) {
            var c = value.components().get(i);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id,specification_version_id,ingredient_id,raw_phrase,
                      match_rule,match_status,sequence_no) VALUES (?,?,?,?,?,'MATCHED',?)
                    """, id(), id, c.ingredientId(), c.rawPhrase(), c.matchRule(), i + 1);
        }
        return id;
    }
    public void releaseSpecification(String id) {
        jdbc.update("UPDATE ingredient_specification_version SET lifecycle_status='RELEASED',released_at=UTC_TIMESTAMP() WHERE specification_version_id=?", id);
    }
    /** Caller holds the product row lock; all versions and items are append-only after creation. */
    public String formula(Formula value, String actor) {
        var previous = jdbc.queryForList("SELECT version_number FROM formula_version WHERE product_id=? ORDER BY version_number DESC LIMIT 1 FOR UPDATE",
                Integer.class, value.productId());
        int version = previous.isEmpty() ? 1 : previous.getFirst() + 1;
        String id = id();
        jdbc.update("""
                INSERT INTO formula_version(formula_version_id,product_id,version_number,lifecycle_status,
                  is_current_released,created_by_user_id,data_provenance_id) VALUES (?,?,?,'DRAFT','N',?,?)
                """, id, value.productId(), version, actor, value.provenanceId());
        for (int i = 0; i < value.items().size(); i++) {
            var item = value.items().get(i);
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id,formula_version_id,supplier_material_id,
                      specification_version_id,sequence_no,quantity_value,quantity_unit) VALUES (?,?,?,?,?,?,?)
                    """, id(), id, item.materialId(), item.specificationId(), i + 1, item.quantity(), item.unit());
        }
        return id;
    }
    public void releaseFormula(String id, String productId, String actor) {
        // Current selection is metadata; historical content, release actors and timestamps remain unchanged.
        jdbc.update("UPDATE formula_version SET is_current_released='N' WHERE product_id=? AND is_current_released='Y'", productId);
        jdbc.update("""
                UPDATE formula_version SET lifecycle_status='RELEASED',is_current_released='Y',
                  released_by_user_id=?,released_at=UTC_TIMESTAMP() WHERE formula_version_id=?
                """, actor, id);
        jdbc.update("UPDATE product SET current_formula_version_id=? WHERE product_id=?", id, productId);
    }
}
