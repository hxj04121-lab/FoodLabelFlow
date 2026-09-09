package com.spectrace.workflow.domain;

import com.spectrace.identity.application.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MakerCheckerPolicyTest {

    private final MakerCheckerPolicy policy = new MakerCheckerPolicy();

    @Test
    void rejectsSelfApproval() {
        assertThrows(
                AuthorizationDeniedException.class,
                () -> policy.requireIndependentChecker(
                        "user_approver",
                        "user_approver"
                )
        );
    }

    @Test
    void allowsIndependentChecker() {
        assertDoesNotThrow(
                () -> policy.requireIndependentChecker(
                        "user_label_officer",
                        "user_approver"
                )
        );
    }
}