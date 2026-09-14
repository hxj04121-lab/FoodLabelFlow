package com.spectrace.validation.application.contract;

import java.util.List;
import java.util.Objects;

/**
 * A derived allergen fact with explicit presence semantics and source
 * ingredient evidence.
 */
public record AllergenFact(
        String allergenId,
        String allergenCode,
        Presence presence,
        List<String> sourceIngredientIds,
        String derivationEvidence
) {

    public AllergenFact {
        allergenId = required(allergenId, "allergenId");
        allergenCode = required(allergenCode, "allergenCode");
        presence = Objects.requireNonNull(presence, "presence");
        if (sourceIngredientIds == null
                || sourceIngredientIds.isEmpty()
                || sourceIngredientIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("sourceIngredientIds must contain 1 or more non-blank entries");
        }
        sourceIngredientIds = sourceIngredientIds.stream().map(String::trim).toList();
        derivationEvidence = required(derivationEvidence, "derivationEvidence");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** CONTAINS and MAY_CONTAIN remain structurally distinct. */
    public enum Presence {
        CONTAINS,
        MAY_CONTAIN
    }
}
