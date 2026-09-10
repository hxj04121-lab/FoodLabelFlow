package com.spectrace.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Input validation is shared by HTTP and application callers. IDs/versions are server assigned. */
public final class CatalogCommands {
    private CatalogCommands() { }
    public static String required(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.length() > limit)
            throw CatalogFailure.invalid(field + " is required and must fit " + limit + " characters");
        return value.trim();
    }
    public static String optional(String value, int limit, String field) {
        if (value == null || value.isBlank()) return null;
        return required(value, limit, field);
    }
    private static <T> List<T> items(List<T> value) {
        if (value == null || value.isEmpty() || value.size() > 100 || value.stream().anyMatch(java.util.Objects::isNull))
            throw CatalogFailure.invalid("items must contain 1 to 100 non-null entries");
        return List.copyOf(value);
    }
    public record Supplier(String code, String name, String provenanceId) {
        public Supplier {
            code = required(code, 80, "code"); name = required(name, 200, "name");
            provenanceId = required(provenanceId, 100, "provenanceId");
        }
    }
    public record Material(String supplierId, String ingredientId, String code, String name,
                           String description, String provenanceId) {
        public Material {
            supplierId = required(supplierId, 80, "supplierId");
            ingredientId = optional(ingredientId, 80, "ingredientId");
            code = required(code, 100, "code"); name = required(name, 200, "name");
            description = optional(description, 600, "description");
            provenanceId = required(provenanceId, 100, "provenanceId");
        }
    }
    public record Component(String ingredientId, String rawPhrase, String matchRule) {
        public Component {
            ingredientId = required(ingredientId, 80, "ingredientId");
            rawPhrase = required(rawPhrase, 300, "rawPhrase");
            matchRule = required(matchRule, 500, "matchRule");
        }
    }
    public record Specification(String materialId, LocalDate effectiveDate, String provenanceId,
                                List<Component> components) {
        public Specification {
            materialId = required(materialId, 80, "materialId");
            if (effectiveDate == null) throw CatalogFailure.invalid("effectiveDate is required");
            provenanceId = required(provenanceId, 100, "provenanceId");
            components = items(components);
        }
    }
    public record Item(String materialId, String specificationId, BigDecimal quantity, String unit) {
        public Item {
            materialId = required(materialId, 80, "materialId");
            specificationId = required(specificationId, 100, "specificationId");
            unit = optional(unit, 40, "unit");
            if ((quantity == null) != (unit == null))
                throw CatalogFailure.invalid("quantity and unit must be supplied together");
            if (quantity != null && (quantity.signum() <= 0 || quantity.scale() > 4
                    || quantity.compareTo(new BigDecimal("99999999.9999")) > 0))
                throw CatalogFailure.invalid("quantity must be positive and fit DECIMAL(12,4)");
        }
    }
    public record Formula(String productId, String provenanceId, List<Item> items) {
        public Formula {
            productId = required(productId, 100, "productId");
            provenanceId = required(provenanceId, 100, "provenanceId");
            items = CatalogCommands.items(items);
        }
    }
    public record Release(String expectedCurrentFormulaId) {
        public Release { expectedCurrentFormulaId = optional(expectedCurrentFormulaId, 120, "expectedCurrentFormulaId"); }
    }
}
