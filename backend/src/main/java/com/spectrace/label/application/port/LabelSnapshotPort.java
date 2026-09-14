package com.spectrace.label.application.port;

import java.util.Optional;

/** Label-owned read boundary; callers must not read label repositories or tables. */
public interface LabelSnapshotPort {
    /**
     * Read the exact requested version and its declarations in the caller's transaction.
     * Empty means absent (404); an existing non-current snapshot is returned (409).
     * The adapter must protect the snapshot from changes until validation commits.
     */
    Optional<LabelValidationSnapshot> findById(String labelVersionId);
}
