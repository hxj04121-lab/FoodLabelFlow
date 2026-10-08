package com.spectrace.label.application.port;

/** First-draft binding only; existing task targets and declaration snapshots are immutable. */
public interface ReviewTaskDraftBinding {
    void requireFirstDraftAvailable(String productId, String jurisdictionCode, String expectedReviewTaskId);

    default void bindOpenTaskToDraft(String productId, String jurisdictionCode, String labelVersionId) {
        bindOpenTaskToDraft(productId, jurisdictionCode, labelVersionId, null);
    }

    void bindOpenTaskToDraft(String productId, String jurisdictionCode, String labelVersionId, String expectedReviewTaskId);
}
