package com.spectrace.validation.application.port;

import java.util.Objects;

/** Identity/RBAC application port; the identity module supplies the adapter. */
public interface AuthorizationPort {

    AuthorizationDecision authorize(String actorId, Permission permission);

    enum Permission {
        VALIDATE_LABEL
    }

    record AuthorizationDecision(boolean allowed, String denialCode) {
        public AuthorizationDecision {
            if (allowed && denialCode != null) {
                throw new IllegalArgumentException("allowed decisions must not have a denialCode");
            }
            if (!allowed && (denialCode == null || denialCode.isBlank())) {
                throw new IllegalArgumentException("denied decisions require a denialCode");
            }
            denialCode = denialCode == null ? null : denialCode.trim();
        }

        public static AuthorizationDecision granted() {
            return new AuthorizationDecision(true, null);
        }

        public static AuthorizationDecision denied(String denialCode) {
            return new AuthorizationDecision(false, Objects.requireNonNull(denialCode, "denialCode"));
        }
    }
}
