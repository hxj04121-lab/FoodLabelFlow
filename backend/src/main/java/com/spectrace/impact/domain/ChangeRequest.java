package com.spectrace.impact.domain;

import java.time.Instant;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * A typed change. changeType selects which version pair versionChange refers to
 * (specification, formula or rule set), mirroring chk_change_request_typed_refs_v3,
 * so a request can never carry references of the wrong kind.
 */
public record ChangeRequest(
        String changeRequestId,
        String changeRequestCode,
        ChangeType changeType,
        ChangeRequestStatus status,
        Instant requestedAt,
        String requestedByUserId,
        String description,
        VersionChange versionChange,
        String dataProvenanceId
) {
    public ChangeRequest {
        changeRequestId = requiredText(changeRequestId, "changeRequestId");
        changeRequestCode = requiredText(changeRequestCode, "changeRequestCode");
        changeType = Objects.requireNonNull(changeType, "changeType");
        status = Objects.requireNonNull(status, "status");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        requestedByUserId = requiredText(requestedByUserId, "requestedByUserId");
        description = requiredText(description, "description");
        versionChange = Objects.requireNonNull(versionChange, "versionChange");
        dataProvenanceId = requiredText(dataProvenanceId, "dataProvenanceId");
    }

    /** The before/after version IDs of the kind named by the request's changeType. */
    public record VersionChange(String fromVersionId, String toVersionId) {
        public VersionChange {
            fromVersionId = requiredText(fromVersionId, "fromVersionId");
            toVersionId = requiredText(toVersionId, "toVersionId");
            if (fromVersionId.equals(toVersionId)) {
                throw new IllegalArgumentException("A change must reference two different versions");
            }
        }
    }
}
