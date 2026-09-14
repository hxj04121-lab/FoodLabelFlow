package com.spectrace.validation.application.port;

import com.spectrace.validation.application.contract.LabelSnapshotLookup;

/** Label module application port; implementations belong to the label owner. */
public interface LabelSnapshotPort {

    LabelSnapshotLookup loadCurrentSnapshot(String labelVersionId);
}
