package com.spectrace.workflow.application;

import java.time.LocalDateTime;

/** Read-only task binding for recovering the outcome of an HTTP command. */
public record ReviewTaskView(String reviewTaskId, String productId,
        String currentLabelVersionId, String draftLabelVersionId, String targetLabelVersionId,
        String status, String decision, LocalDateTime resolvedAt) {}
