package com.spectrace.validation.application.contract;

import java.math.BigDecimal;
import java.util.List;

/** Immutable formula item with its pinned specification components. */
public record FormulaItem(
        String formulaItemId,
        String materialId,
        String specificationId,
        BigDecimal quantity,
        String unit,
        List<FormulaComponent> components
) {

    public FormulaItem {
        formulaItemId = required(formulaItemId, "formulaItemId");
        materialId = required(materialId, "materialId");
        specificationId = required(specificationId, "specificationId");
        unit = optional(unit, "unit");
        if ((quantity == null) != (unit == null)) {
            throw new IllegalArgumentException("quantity and unit must be supplied together");
        }
        if (quantity != null && quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (components == null || components.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("components must not contain null entries");
        }
        components = List.copyOf(components);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field);
    }
}
