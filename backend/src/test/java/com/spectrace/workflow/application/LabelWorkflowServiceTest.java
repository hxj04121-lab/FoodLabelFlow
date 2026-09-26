package com.spectrace.workflow.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LabelWorkflowServiceTest {

    private final LabelReviewService reviewService =
            mock(LabelReviewService.class);

    private final LabelWorkflowService service =
            new LabelWorkflowService(reviewService);

    @Test
    void delegatesSubmitToJavaReviewService() {
        AuthenticatedActor actor = actor(
                "user_label_officer",
                "LABEL.SUBMIT_REVIEW"
        );

        service.submitForReview(
                "label_test",
                actor
        );

        verify(reviewService).submitForReview(
                "label_test",
                actor
        );
    }

    @Test
    void delegatesDecisionToJavaReviewService() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        service.recordDecision(
                "label_test",
                "APPROVE",
                "Approved",
                actor
        );

        verify(reviewService).recordDecision(
                "label_test",
                "APPROVE",
                "Approved",
                actor
        );
    }

    private AuthenticatedActor actor(
            String userId,
            String... permissions
    ) {
        return new AuthenticatedActor(
                userId,
                userId,
                userId,
                Set.of(),
                Set.of(permissions)
        );
    }
}