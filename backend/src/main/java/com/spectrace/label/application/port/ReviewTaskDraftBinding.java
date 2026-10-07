package com.spectrace.label.application.port;

/** Binds a newly created label draft to its matching open impact review task. */
public interface ReviewTaskDraftBinding {

    void bindOpenTaskToDraft(
            String productId,
            String jurisdictionCode,
            String labelVersionId
    );
}
