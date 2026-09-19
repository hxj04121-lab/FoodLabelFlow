package com.spectrace.validation.domain;

import java.util.List;
import java.util.Objects;

public enum ValidationStatus {
    PASSED,
    FAILED;

    /** A completed run fails only when at least one finding is failed and blocking. */
    public static ValidationStatus fromResults(List<ValidationResult> results) {
        Objects.requireNonNull(results, "results");
        for (ValidationResult result : results) {
            if (result == null) {
                throw new IllegalArgumentException("results must not contain null entries");
            }
            if (result.blocking() && !result.passed()) {
                return FAILED;
            }
        }
        return PASSED;
    }

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
