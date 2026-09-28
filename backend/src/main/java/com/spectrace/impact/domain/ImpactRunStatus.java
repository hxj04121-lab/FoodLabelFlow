package com.spectrace.impact.domain;

public enum ImpactRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    public static ImpactRunStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("impact run status is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported impact run status: " + value, error);
        }
    }
}
