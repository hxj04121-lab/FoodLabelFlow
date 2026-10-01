package com.spectrace.impact.interfaces.web;

import com.spectrace.impact.application.ChangeRequestView;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;

/** The S3 contract ChangeRequest resource; actor, code and provenance stay internal. */
public record ChangeRequestResponse(
        String changeRequestId,
        ChangeType changeType,
        String supplierMaterialId,
        String previousSpecificationVersionId,
        String targetSpecificationVersionId,
        String description,
        ChangeRequestStatus status,
        String createdAt
) {
    public static ChangeRequestResponse from(ChangeRequestView view) {
        var request = view.changeRequest();
        return new ChangeRequestResponse(
                request.changeRequestId(),
                request.changeType(),
                view.supplierMaterialId(),
                request.versionChange().fromVersionId(),
                request.versionChange().toVersionId(),
                request.description(),
                request.status(),
                request.requestedAt().toString());
    }
}
