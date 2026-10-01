package com.spectrace.impact.domain;

public enum ReviewTaskStatus {
    OPEN,
    IN_REVIEW,
    CLOSED;

    public static ReviewTaskStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("review task status is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported review task status: " + value, error);
        }
    }
}
