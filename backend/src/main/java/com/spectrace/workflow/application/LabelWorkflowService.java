package com.spectrace.workflow.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import org.springframework.stereotype.Service;

/**
 * Backward-compatible workflow facade.
 *
 * Review submission and decisions are implemented by
 * LabelReviewService in the Java application/domain layer.
 */
@Service
public class LabelWorkflowService {

    private final LabelReviewService reviewService;

    public LabelWorkflowService(
            LabelReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    public void submitForReview(
            String labelVersionId,
            AuthenticatedActor actor
    ) {
        reviewService.submitForReview(
                labelVersionId,
                actor
        );
    }

    public void recordDecision(
            String labelVersionId,
            String decision,
            String comments,
            AuthenticatedActor actor
    ) {
        reviewService.recordDecision(
                labelVersionId,
                decision,
                comments,
                actor
        );
    }
}