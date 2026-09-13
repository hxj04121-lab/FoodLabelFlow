package com.spectrace.validation.domain;

public enum RuleSetLifecycleStatus {
    DRAFT,
    ACTIVE,
    RETIRED;

    public static RuleSetLifecycleStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("rule-set lifecycle status is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported rule-set lifecycle status: " + value, error);
        }
    }
}
