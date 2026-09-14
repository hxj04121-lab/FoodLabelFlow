package com.spectrace.validation.domain;

public enum ValidationSeverity {
    INFO,
    WARNING,
    ERROR;

    public static ValidationSeverity fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("validation severity is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported validation severity: " + value, error);
        }
    }
}
