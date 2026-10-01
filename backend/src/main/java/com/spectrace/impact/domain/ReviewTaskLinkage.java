package com.spectrace.impact.domain;

import java.time.Instant;
import java.util.Objects;

public record ReviewTaskLinkage(
        String reviewTaskId,
        String impactFindingId,
        String productId,
        String currentLabelVersionId,
        String draftLabelVersionId,
        ReviewTaskStatus status,
        String assignedToUserId,
        String createdByUserId,
        Instant createdAt,
        String dataProvenanceId
) {
    public ReviewTaskLinkage {
        reviewTaskId = required(reviewTaskId, "reviewTaskId");
        impactFindingId = required(impactFindingId, "impactFindingId");
        productId = required(productId, "productId");
        currentLabelVersionId = required(currentLabelVersionId, "currentLabelVersionId");
        status = Objects.requireNonNull(status, "status");
        assignedToUserId = required(assignedToUserId, "assignedToUserId");
        createdByUserId = required(createdByUserId, "createdByUserId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
