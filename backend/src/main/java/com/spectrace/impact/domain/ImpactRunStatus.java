package com.spectrace.impact.domain;

/** V2 impact_analysis_run.status tokens. */
public enum ImpactRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    /** A terminal run has a completion time and accepts no further findings. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

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
