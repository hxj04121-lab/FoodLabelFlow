package com.spectrace.validation.application.contract;

import java.util.Objects;

/**
 * An immutable component from a formula item's pinned specification.
 *
 * <p>The nullable ingredient id is intentional: an unmapped or ambiguous
 * component must remain visible to validation instead of being silently
 * discarded or replaced with seed data.</p>
 */
public record FormulaComponent(
        String ingredientId,
        String rawPhrase,
        String matchRule,
        MatchStatus matchStatus
) {

    public FormulaComponent {
        rawPhrase = required(rawPhrase, "rawPhrase");
        matchRule = required(matchRule, "matchRule");
        matchStatus = Objects.requireNonNull(matchStatus, "matchStatus");
        if (matchStatus == MatchStatus.MATCHED) {
            ingredientId = required(ingredientId, "ingredientId");
        } else if (ingredientId != null && ingredientId.isBlank()) {
            throw new IllegalArgumentException("ingredientId must be null or non-blank");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public enum MatchStatus {
        MATCHED,
        UNMAPPED,
        AMBIGUOUS
    }
}
