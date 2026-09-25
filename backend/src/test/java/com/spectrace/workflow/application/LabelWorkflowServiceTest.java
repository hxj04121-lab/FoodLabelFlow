package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LabelWorkflowServiceTest {

    private final AuthorizationService authorizationService =
            new AuthorizationService();

    private final LabelWorkflowRepository workflowRepository =
            mock(LabelWorkflowRepository.class);

    private final MakerCheckerPolicy makerCheckerPolicy =
            new MakerCheckerPolicy();

    private final LabelWorkflowService service =
            new LabelWorkflowService(
                    authorizationService,
                    workflowRepository,
                    makerCheckerPolicy
            );

    @Test
    void allowsAuthorizedMakerToSubmitForReview() {
        AuthenticatedActor actor = actor(
                "user_label_officer",
                "LABEL.SUBMIT_REVIEW"
        );

        when(workflowRepository.findVersion("label_test"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_test",
                                "DRAFT",
                                true
                        )
                ));

        service.submitForReview("label_test", actor);

        verify(workflowRepository).submitForReview(
                "label_test",
                "user_label_officer"
        );
    }

    @Test
    void rejectsSubmitWithoutSubmitReviewPermission() {
        AuthenticatedActor actor = actor(
                "user_unauthorized"
        );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.submitForReview(
                        "label_test",
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void allowsIndependentCheckerToApprove() {
        AuthenticatedActor checker = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        when(workflowRepository.findVersion("label_test"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_test",
                                "PENDING_REVIEW",
                                true
                        )
                ));

        when(workflowRepository.findCreatorUserId("label_test"))
                .thenReturn(Optional.of("user_label_officer"));

        service.recordDecision(
                "label_test",
                "APPROVE",
                "Approved by independent checker",
                checker
        );

        verify(workflowRepository).recordDecision(
                "label_test",
                "APPROVE",
                "user_approver",
                "Approved by independent checker"
        );
    }

    @Test
    void rejectsSelfApprovalEvenWhenActorHasApprovePermission() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        when(workflowRepository.findVersion("label_self"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_self",
                                "PENDING_REVIEW",
                                true
                        )
                ));

        when(workflowRepository.findCreatorUserId("label_self"))
                .thenReturn(Optional.of("user_approver"));

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.recordDecision(
                        "label_self",
                        "APPROVE",
                        "Attempted self approval",
                        actor
                )
        );

        verify(workflowRepository, never()).recordDecision(
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsApprovalWithoutApprovePermission() {
        AuthenticatedActor actor = actor(
                "user_label_officer"
        );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.recordDecision(
                        "label_test",
                        "APPROVE",
                        "Unauthorized approval",
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void allowsAuthorizedCheckerToReject() {
        AuthenticatedActor checker = actor(
                "user_approver",
                "LABEL.REJECT"
        );

        when(workflowRepository.findVersion("label_test"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_test",
                                "PENDING_REVIEW",
                                true
                        )
                ));

        service.recordDecision(
                "label_test",
                "REJECT",
                "Rejected by checker",
                checker
        );

        verify(workflowRepository).recordDecision(
                "label_test",
                "REJECT",
                "user_approver",
                "Rejected by checker"
        );
    }

    @Test
    void rejectsRejectionWithoutRejectPermission() {
        AuthenticatedActor actor = actor(
                "user_label_officer"
        );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.recordDecision(
                        "label_test",
                        "REJECT",
                        "Unauthorized rejection",
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void rejectsNullActor() {
        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.submitForReview(
                        "label_test",
                        null
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void rejectsNullDecision() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordDecision(
                        "label_test",
                        null,
                        null,
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void rejectsBlankDecision() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordDecision(
                        "label_test",
                        " ",
                        null,
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void rejectsUnsupportedDecision() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordDecision(
                        "label_test",
                        "PUBLISH",
                        null,
                        actor
                )
        );

        verifyNoInteractions(workflowRepository);
    }

    @Test
    void rejectsSubmitForStaleLabelVersion() {
        AuthenticatedActor actor = actor(
                "user_label_officer",
                "LABEL.SUBMIT_REVIEW"
        );

        when(workflowRepository.findVersion("label_stale"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_stale",
                                "DRAFT",
                                false
                        )
                ));

        assertThrows(
                LabelVersionConflictException.class,
                () -> service.submitForReview(
                        "label_stale",
                        actor
                )
        );

        verify(workflowRepository, never()).submitForReview(
                anyString(),
                anyString()
        );
    }

    @Test
    void rejectsDecisionForHistoricalLabelVersion() {
        AuthenticatedActor actor = actor(
                "user_approver",
                "LABEL.APPROVE"
        );

        when(workflowRepository.findVersion("label_superseded"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_superseded",
                                "SUPERSEDED",
                                false
                        )
                ));

        assertThrows(
                LabelVersionConflictException.class,
                () -> service.recordDecision(
                        "label_superseded",
                        "APPROVE",
                        "Historical labels are immutable",
                        actor
                )
        );

        verify(workflowRepository, never()).recordDecision(
                anyString(),
                anyString(),
                anyString(),
                any()
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