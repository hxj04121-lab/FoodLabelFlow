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
    void rejectsSelfApprovalEvenWhenActorHasApprovePermission() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE")
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
    void rejectsNullDecision() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE")
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
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE")
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
    void allowsSubmitForCurrentLabelVersion() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_label_officer",
                "label.officer",
                "Demo Label Officer",
                Set.of("LABEL_OFFICER"),
                Set.of("LABEL.SUBMIT_REVIEW")
        );

        when(workflowRepository.findVersion("label_current"))
                .thenReturn(Optional.of(
                        new LabelWorkflowRepository.LabelWorkflowVersion(
                                "label_current",
                                "DRAFT",
                                true
                        )
                ));

        service.submitForReview(
                "label_current",
                actor
        );

        verify(workflowRepository).submitForReview(
                "label_current",
                "user_label_officer"
        );
    }

    @Test
    void rejectsSubmitForStaleLabelVersion() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_label_officer",
                "label.officer",
                "Demo Label Officer",
                Set.of("LABEL_OFFICER"),
                Set.of("LABEL.SUBMIT_REVIEW")
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
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE")
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
}