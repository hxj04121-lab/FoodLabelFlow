package com.spectrace.impact.domain;

public enum ImpactFindingClassification {
    NO_ACTION,
    REVIEW_REQUIRED;

    public static ImpactFindingClassification fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("impact finding classification is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported impact finding classification: " + value, error);
        }
    }
}
