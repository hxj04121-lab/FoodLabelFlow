package com.spectrace.label.application.port;

/** Explicit first binding and returned-task revision; declaration snapshots stay immutable. */
public interface ReviewTaskDraftBinding {
    void requireFirstDraftAvailable(String productId, String jurisdictionCode, String expectedReviewTaskId);

    default void bindOpenTaskToDraft(String productId, String jurisdictionCode, String labelVersionId) {
        bindOpenTaskToDraft(productId, jurisdictionCode, labelVersionId, null);
    }

    void bindOpenTaskToDraft(String productId, String jurisdictionCode, String labelVersionId, String expectedReviewTaskId);

    RevisionTarget requireReturnedDraftAvailable(String reviewTaskId, String expectedLabelVersionId);

    void bindReturnedTaskToRevision(String reviewTaskId, String expectedLabelVersionId,
                                    String newLabelVersionId, String actorUserId, String dataProvenanceId);

    record RevisionTarget(String productId, String jurisdictionCode) {}
}
