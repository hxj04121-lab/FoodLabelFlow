package com.spectrace.impact.domain;

/** V2 impact_finding.classification tokens. */
public enum ImpactClassification {
    NO_ACTION,
    REVIEW_REQUIRED;

    public boolean requiresReviewTask() {
        return this == REVIEW_REQUIRED;
    }

    public static ImpactClassification fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("impact classification is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported impact classification: " + value, error);
        }
    }
}
