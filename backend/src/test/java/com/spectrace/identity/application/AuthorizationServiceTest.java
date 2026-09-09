package com.spectrace.identity.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationServiceTest {

    private final AuthorizationService authorizationService =
            new AuthorizationService();

    @Test
    void allowsActorWithRequiredPermission() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE", "LABEL.REJECT")
        );

        assertDoesNotThrow(() ->
                authorizationService.requirePermission(
                        actor,
                        "LABEL.APPROVE"
                )
        );
    }

    @Test
    void deniesActorWithoutRequiredPermission() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_label_officer",
                "label.officer",
                "Demo Label Officer",
                Set.of("LABEL_OFFICER"),
                Set.of(
                        "LABEL.CREATE",
                        "LABEL.VALIDATE",
                        "LABEL.SUBMIT_REVIEW"
                )
        );

        assertThrows(
                AuthorizationDeniedException.class,
                () -> authorizationService.requirePermission(
                        actor,
                        "LABEL.APPROVE"
                )
        );
    }

    @Test
    void deniesMissingActor() {
        assertThrows(
                AuthorizationDeniedException.class,
                () -> authorizationService.requirePermission(
                        null,
                        "LABEL.APPROVE"
                )
        );
    }

    @Test
    void rejectsBlankPermission() {
        AuthenticatedActor actor = new AuthenticatedActor(
                "user_approver",
                "qa.approver",
                "Demo Approver",
                Set.of("APPROVER"),
                Set.of("LABEL.APPROVE")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> authorizationService.requirePermission(actor, " ")
        );
    }
}