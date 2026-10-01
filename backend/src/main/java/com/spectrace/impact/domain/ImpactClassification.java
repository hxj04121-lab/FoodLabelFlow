package com.spectrace.impact.domain;

/** V2 impact_finding.classification tokens. */
public enum ImpactClassification {
    /** Every derived allergen is already declared on the published label; no task. */
    NO_ACTION,
    /** At least one derived allergen is missing from the published label; one ReviewTask. */
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
