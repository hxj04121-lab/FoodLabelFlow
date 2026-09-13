package com.spectrace.validation.domain;

public enum ValidationStatus {
    PASSED,
    FAILED;

    public static ValidationStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("validation status is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported validation status: " + value, error);
        }
    }
}
