package com.spectrace.validation.application.contract;

import java.util.Objects;

/**
 * Explicit label lookup outcome so adapters can preserve 404 (missing) and
 * 409 (non-current) semantics without parsing messages.
 */
public record LabelSnapshotLookup(
        Status status,
        LabelValidationSnapshot snapshot
) {

    public LabelSnapshotLookup {
        status = Objects.requireNonNull(status, "status");
        if (status == Status.FOUND) {
            if (snapshot == null || !snapshot.current()) {
                throw new IllegalArgumentException("FOUND requires a current snapshot");
            }
        } else if (snapshot != null) {
            throw new IllegalArgumentException(status + " must not expose a snapshot");
        }
    }

    public static LabelSnapshotLookup found(LabelValidationSnapshot snapshot) {
        return new LabelSnapshotLookup(Status.FOUND, snapshot);
    }

    public static LabelSnapshotLookup notFound() {
        return new LabelSnapshotLookup(Status.NOT_FOUND, null);
    }

    public static LabelSnapshotLookup notCurrent() {
        return new LabelSnapshotLookup(Status.NOT_CURRENT, null);
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        NOT_CURRENT
    }
}
