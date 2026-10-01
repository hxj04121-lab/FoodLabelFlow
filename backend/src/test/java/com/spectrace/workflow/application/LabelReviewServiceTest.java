package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository.ReviewTarget;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LabelReviewServiceTest {

    private final AuthorizationService authorizationService =
            new AuthorizationService();

    private final LabelReviewCommandRepository repository =
            mock(LabelReviewCommandRepository.class);

    private final LabelTransitionPolicy transitionPolicy =
            new LabelTransitionPolicy();

    private final MakerCheckerPolicy makerCheckerPolicy =
            new MakerCheckerPolicy();

    private final LabelReviewService service =
            new LabelReviewService(
                    authorizationService,
                    repository,
                    transitionPolicy,
                    makerCheckerPolicy
            );

    @Test
    void submitsCurrentValidatedDraftForReview() {
        when(repository.lockForReview("label_v1"))
                .thenReturn(Optional.of(target(
                        "DRAFT",
                        true
                )));

        when(repository.hasPassingValidation(
                "label_v1",
                "rules_v1"
        )).thenReturn(true);

        when(repository.markPendingReview("label_v1"))
                .thenReturn(1);

        service.submitForReview(
                "label_v1",
                submitter()
        );

        verify(repository)
                .markPendingReview("label_v1");

        verify(repository)
                .markReviewTaskInReview("label_v1");

        verify(repository)
                .createSubmitAudit(
                        "label_v1",
                        "user_submitter",
                        "prov_1"
                );
    }

    @Test
    void rejectsSubmissionWithoutPermission() {
        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.submitForReview(
                        "label_v1",
                        noPermissionActor()
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsStaleLabel() {
        when(repository.lockForReview("label_v1"))
                .thenReturn(Optional.of(target(
                        "DRAFT",
                        false
                )));

        assertThrows(
                LabelVersionConflictException.class,
                () -> service.submitForReview(
                        "label_v1",
                        submitter()
                )
        );

        verify(repository, never())
                .markPendingReview(anyString());
    }

    @Test
    void rejectsInvalidLifecycleTransition() {
        when(repository.lockForReview("label_v1"))
                .thenReturn(Optional.of(target(
                        "PENDING_REVIEW",
                        true
                )));

        assertThrows(
                IllegalStateException.class,
                () -> service.submitForReview(
                        "label_v1",
                        submitter()
                )
        );

        verify(repository, never())
                .markPendingReview(anyString());
    }

    @Test
    void rejectsSubmissionWithoutPassingLatestValidation() {
        when(repository.lockForReview("label_v1"))
                .thenReturn(Optional.of(target(
                        "DRAFT",
                        true
                )));

        when(repository.hasPassingValidation(
                "label_v1",
                "rules_v1"
        )).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> service.submitForReview(
                        "label_v1",
                        submitter()
                )
        );

        verify(repository, never())
                .markPendingReview(anyString());
    }

    private ReviewTarget target(
            String status,
            boolean current
    ) {
        return new ReviewTarget(
                "label_v1",
                "rules_v1",
                status,
                current,
                true,
                "prov_1"
        );
    }

    private AuthenticatedActor submitter() {
        return new AuthenticatedActor(
                "user_submitter",
                "submitter",
                "Submitter",
                Set.of(),
                Set.of("LABEL.SUBMIT_REVIEW")
        );
    }

    private AuthenticatedActor noPermissionActor() {
        return new AuthenticatedActor(
                "user_reader",
                "reader",
                "Reader",
                Set.of(),
                Set.of()
        );
    }

    @Test
    void rejectsSelfApproval() {
        when(repository.lockForDecision("label_v1"))
                .thenReturn(Optional.of(
                        new LabelReviewCommandRepository.DecisionTarget(
                                "label_v1",
                                "PENDING_REVIEW",
                                "user_creator",
                                "review_1",
                                true,
                                "prov_1"
                        )
                ));

        AuthenticatedActor creatorApprover =
                new AuthenticatedActor(
                        "user_creator",
                        "creator",
                        "Creator",
                        Set.of(),
                        Set.of("LABEL.APPROVE")
                );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.recordDecision(
                        "label_v1",
                        "APPROVE",
                        "self approval",
                        creatorApprover
                )
        );

        verify(repository, never())
                .updateDecisionState(
                        anyString(),
                        anyString()
                );

        verify(repository, never())
                .createApprovalRecord(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString()
                );
    }

    @Test
    void rejectsDecisionWithoutPermission() {
        AuthenticatedActor actor =
                new AuthenticatedActor(
                        "user_reader",
                        "reader",
                        "Reader",
                        Set.of(),
                        Set.of()
                );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.recordDecision(
                        "label_v1",
                        "APPROVE",
                        "no permission",
                        actor
                )
        );

        verify(repository, never())
                .lockForDecision(anyString());
    }

    @Test
    void rejectsPendingReviewLabelToRejected() {
        when(repository.lockForDecision("label_v1"))
                .thenReturn(Optional.of(
                        new LabelReviewCommandRepository.DecisionTarget(
                                "label_v1",
                                "PENDING_REVIEW",
                                "user_creator",
                                "review_1",
                                true,
                                "prov_1"
                        )
                ));

        AuthenticatedActor rejector =
                new AuthenticatedActor(
                        "user_reviewer",
                        "reviewer",
                        "Reviewer",
                        Set.of(),
                        Set.of("LABEL.REJECT")
                );

        when(repository.updateDecisionState(
                "label_v1",
                "REJECTED"
        )).thenReturn(1);

        service.recordDecision(
                "label_v1",
                "REJECT",
                "Rejected during review",
                rejector
        );

        verify(repository).updateDecisionState(
                "label_v1",
                "REJECTED"
        );

        verify(repository).createApprovalRecord(
                "label_v1",
                "review_1",
                "REJECT",
                "user_reviewer",
                "Rejected during review",
                "prov_1"
        );

        verify(repository).createDecisionAudit(
                "label_v1",
                "REJECT",
                "PENDING_REVIEW",
                "REJECTED",
                "user_reviewer",
                "prov_1"
        );
    }}