package com.spectrace.label.domain;

import java.time.LocalDateTime;

public record LabelDraft(
        String labelVersionId,
        String productId,
        String formulaVersionId,
        String ruleSetVersionId,
        String jurisdictionCode,
        int versionNumber,
        String rawIngredientText,
        String lifecycleStatus,
        String isCurrentPublished,
        String createdByUserId,
        LocalDateTime createdAt,
        String dataProvenanceId
) {
}