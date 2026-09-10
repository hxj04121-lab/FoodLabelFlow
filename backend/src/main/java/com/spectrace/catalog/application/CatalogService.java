package com.spectrace.catalog.application;

import com.spectrace.catalog.domain.CatalogCommands.*;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogStore store;
    private final ObjectProvider<CatalogIntegration> integration;
    public CatalogService(CatalogStore store, ObjectProvider<CatalogIntegration> integration) {
        this.store = store; this.integration = integration;
    }
    private CatalogIntegration integration() {
        var adapter = integration.getIfAvailable();
        if (adapter == null) throw new CatalogFailure(503, "CATALOG_INTEGRATION_UNAVAILABLE",
                "Identity and transactional audit adapter must be configured before catalog writes");
        return adapter;
    }
    public List<Map<String, Object>> list(CatalogStore.Kind kind, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw CatalogFailure.invalid("limit must be 1..100; offset must be nonnegative");
        return store.list(kind, limit, offset);
    }
    public Map<String, Object> get(CatalogStore.Kind kind, String id) {
        var result = new LinkedHashMap<>(store.get(kind, id, false));
        if (kind == SPECIFICATION) result.put("components", store.components(id));
        if (kind == FORMULA) result.put("items", store.formulaItems(id));
        return result;
    }
    public List<Map<String, Object>> versions(String productId) {
        store.get(PRODUCT, productId, false);
        return store.versions(productId);
    }
    @Transactional
    public Map<String, Object> create(Supplier value) {
        var adapter = integration(); String actor = adapter.requireActor("DATA.MAINTAIN");
        store.get(PROVENANCE, value.provenanceId(), false);
        String id = store.supplier(value);
        adapter.audit(actor, "SUPPLIER_CREATED", id, value.provenanceId());
        return get(SUPPLIER, id);
    }
    @Transactional
    public Map<String, Object> create(Material value) {
        var adapter = integration(); String actor = adapter.requireActor("DATA.MAINTAIN");
        store.get(SUPPLIER, value.supplierId(), false);
        store.get(PROVENANCE, value.provenanceId(), false);
        if (value.ingredientId() != null) store.get(INGREDIENT, value.ingredientId(), false);
        String id = store.material(value);
        adapter.audit(actor, "MATERIAL_CREATED", id, value.provenanceId());
        return get(MATERIAL, id);
    }
    @Transactional
    public Map<String, Object> create(Specification value) {
        var adapter = integration(); String actor = adapter.requireActor("DATA.MAINTAIN");
        store.get(MATERIAL, value.materialId(), true);
        store.get(PROVENANCE, value.provenanceId(), false);
        for (var component : value.components()) store.get(INGREDIENT, component.ingredientId(), false);
        String id = store.specification(value, actor);
        adapter.audit(actor, "SPECIFICATION_CREATED", id, value.provenanceId());
        return get(SPECIFICATION, id);
    }
    @Transactional
    public Map<String, Object> releaseSpecification(String id) {
        var adapter = integration(); String actor = adapter.requireActor("DATA.MAINTAIN");
        var spec = store.get(SPECIFICATION, id, true);
        draft(spec);
        if (store.components(id).isEmpty()) throw CatalogFailure.invalid("specification must have components");
        store.releaseSpecification(id);
        adapter.audit(actor, "SPECIFICATION_RELEASED", id, (String) spec.get("data_provenance_id"));
        return get(SPECIFICATION, id);
    }
    @Transactional
    public Map<String, Object> create(Formula value) {
        var adapter = integration(); String actor = adapter.requireActor("DATA.MAINTAIN");
        store.get(PRODUCT, value.productId(), true);
        store.get(PROVENANCE, value.provenanceId(), false);
        for (var item : value.items().stream().sorted(java.util.Comparator.comparing(Item::specificationId)).toList())
            eligible(item.materialId(), item.specificationId());
        String id = store.formula(value, actor);
        adapter.audit(actor, "FORMULA_CREATED", id, value.provenanceId());
        return get(FORMULA, id);
    }
    @Transactional
    public Map<String, Object> releaseFormula(String id, Release request) {
        if (request == null) throw CatalogFailure.invalid("release request is required");
        var adapter = integration(); String actor = adapter.requireActor("FORMULA.RELEASE");
        // Lock parent before child consistently with create; the product pointer is the concurrency token.
        String productId = (String) store.get(FORMULA, id, false).get("product_id");
        var product = store.get(PRODUCT, productId, true);
        var formula = store.get(FORMULA, id, true);
        draft(formula);
        if (!Objects.equals(request.expectedCurrentFormulaId(), product.get("current_formula_version_id")))
            throw new CatalogFailure(409, "CURRENT_FORMULA_CHANGED", "Refresh the product before releasing a formula");
        var items = store.formulaItems(id);
        if (items.isEmpty()) throw CatalogFailure.invalid("formula must have items");
        for (var item : items.stream().sorted(java.util.Comparator.comparing(row -> (String) row.get("specification_version_id"))).toList())
            eligible((String) item.get("supplier_material_id"), (String) item.get("specification_version_id"));
        store.releaseFormula(id, productId, actor);
        adapter.audit(actor, "FORMULA_RELEASED", id, (String) formula.get("data_provenance_id"));
        return get(FORMULA, id);
    }
    private void eligible(String materialId, String specificationId) {
        store.get(MATERIAL, materialId, false);
        var spec = store.get(SPECIFICATION, specificationId, true);
        if (!materialId.equals(spec.get("supplier_material_id")))
            throw new CatalogFailure(422, "SPECIFICATION_MATERIAL_MISMATCH", "Specification belongs to a different material");
        if (!"RELEASED".equals(spec.get("lifecycle_status")))
            throw new CatalogFailure(422, "SPECIFICATION_NOT_RELEASED", "Formula items require released specifications");
        if (((java.sql.Date) spec.get("effective_date")).toLocalDate().isAfter(LocalDate.now(ZoneOffset.UTC)))
            throw new CatalogFailure(422, "SPECIFICATION_NOT_EFFECTIVE", "Specification effective date is in the future");
    }
    private static void draft(Map<String, Object> value) {
        if (!"DRAFT".equals(value.get("lifecycle_status")))
            throw new CatalogFailure(409, "VERSION_IMMUTABLE", "Only a draft can be released; create a new version for changes");
    }
    public Map<String, Object> trace(String id) {
        var formula = get(FORMULA, id);
        var lines = store.formulaItems(id).stream().map(item -> {
            var material = get(MATERIAL, (String) item.get("supplier_material_id"));
            var spec = get(SPECIFICATION, (String) item.get("specification_version_id"));
            return Map.of("item", item, "material", material, "specification", spec,
                    "supplier", get(SUPPLIER, (String) material.get("supplier_id")),
                    "specificationEvidence", get(PROVENANCE, (String) spec.get("data_provenance_id")));
        }).toList();
        return Map.of("formula", formula, "trace", lines);
    }
}
