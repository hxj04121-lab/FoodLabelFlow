package com.spectrace.impact.domain;

/** V2 change_request.status tokens. Transitions are not frozen yet (SCRUM-75 review item). */
public enum ChangeRequestStatus {
    DRAFT,
    SUBMITTED,
    ANALYZED,
    COMPLETED,
    CANCELLED;

    public static ChangeRequestStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("change request status is missing");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported change request status: " + value, error);
        }
    }
}
