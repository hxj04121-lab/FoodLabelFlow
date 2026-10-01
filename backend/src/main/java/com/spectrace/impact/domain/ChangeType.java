package com.spectrace.impact.domain;

/** V2 change_request.change_type tokens; Sprint 3 analyses INGREDIENT_SPEC only. */
public enum ChangeType {
    INGREDIENT_SPEC,
    FORMULA,
    RULE_SET;

    public static ChangeType fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("change type is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported change type: " + value, error);
        }
    }
}
