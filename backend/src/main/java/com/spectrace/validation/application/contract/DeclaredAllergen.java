package com.spectrace.validation.application.contract;

import java.util.Objects;

/** A structured allergen declaration on the label snapshot. */
public record DeclaredAllergen(
        String allergenId,
        DeclarationType declarationType
) {

    public DeclaredAllergen {
        allergenId = required(allergenId, "allergenId");
        declarationType = Objects.requireNonNull(declarationType, "declarationType");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public enum DeclarationType {
        CONTAINS,
        MAY_CONTAIN
    }
}
