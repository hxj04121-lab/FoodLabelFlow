package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
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
}