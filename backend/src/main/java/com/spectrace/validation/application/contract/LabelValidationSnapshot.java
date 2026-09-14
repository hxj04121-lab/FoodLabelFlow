package com.spectrace.validation.application.contract;

import java.util.List;

/**
 * Immutable, version-pinned validation input supplied by the label module.
 *
 * <p>The snapshot contains formula items and their specification components,
 * plus the structured label declarations. It deliberately contains no
 * persistence entity, JDBC type, or foreign repository reference.</p>
 */
public record LabelValidationSnapshot(
        String labelVersionId,
        boolean current,
        String formulaVersionId,
        String jurisdictionCode,
        List<FormulaItem> formulaItems,
        List<DeclaredAllergen> declaredAllergens
) {

    public LabelValidationSnapshot {
        labelVersionId = required(labelVersionId, "labelVersionId");
        formulaVersionId = required(formulaVersionId, "formulaVersionId");
        jurisdictionCode = required(jurisdictionCode, "jurisdictionCode");
        if (formulaItems == null || formulaItems.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("formulaItems must not contain null entries");
        }
        if (declaredAllergens == null || declaredAllergens.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("declaredAllergens must not contain null entries");
        }
        formulaItems = List.copyOf(formulaItems);
        declaredAllergens = List.copyOf(declaredAllergens);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
