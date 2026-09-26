package com.spectrace.workflow.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabelTransitionPolicyTest {

    private final LabelTransitionPolicy policy =
            new LabelTransitionPolicy();

    @Test
    void allowsSupportedLifecycleTransitions() {
        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "DRAFT",
                        "PENDING_REVIEW"
                )
        );

        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "PENDING_REVIEW",
                        "APPROVED"
                )
        );

        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "PENDING_REVIEW",
                        "DRAFT"
                )
        );

        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "PENDING_REVIEW",
                        "REJECTED"
                )
        );

        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "APPROVED",
                        "PUBLISHED"
                )
        );

        assertDoesNotThrow(() ->
                policy.requireTransition(
                        "PUBLISHED",
                        "SUPERSEDED"
                )
        );
    }

    @Test
    void rejectsPublishingDraftLabel() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.requireTransition(
                        "DRAFT",
                        "PUBLISHED"
                )
        );
    }

    @Test
    void rejectsPublishingPendingReviewLabel() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.requirePublishable(
                        "PENDING_REVIEW"
                )
        );
    }

    @Test
    void allowsPublishingApprovedLabel() {
        assertDoesNotThrow(() ->
                policy.requirePublishable(
                        "APPROVED"
                )
        );
    }

    @Test
    void rejectsTransitionFromHistoricalState() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.requireTransition(
                        "SUPERSEDED",
                        "PUBLISHED"
                )
        );
    }
}