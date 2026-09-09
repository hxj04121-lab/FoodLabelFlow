package com.spectrace.workflow.domain;

import com.spectrace.identity.application.AuthorizationDeniedException;
import org.springframework.stereotype.Component;

@Component
public class MakerCheckerPolicy {

    public void requireIndependentChecker(
            String makerUserId,
            String checkerUserId
    ) {
        if (makerUserId == null || checkerUserId == null) {
            throw new IllegalArgumentException(
                    "Maker and checker user IDs are required"
            );
        }

        if (makerUserId.equals(checkerUserId)) {
            throw new AuthorizationDeniedException(
                    "Maker-checker violation: creator cannot approve own label"
            );
        }
    }
}