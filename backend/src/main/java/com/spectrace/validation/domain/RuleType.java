package com.spectrace.validation.domain;

public enum RuleType {
    INGREDIENT_TO_ALLERGEN,
    LABEL_DECLARATION_VALIDATION;

    public static RuleType fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("rule type is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported rule type: " + value, error);
        }
    }
}
