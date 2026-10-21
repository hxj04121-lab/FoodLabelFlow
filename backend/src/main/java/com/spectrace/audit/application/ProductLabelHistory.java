package com.spectrace.audit.application;

import java.time.LocalDateTime;
import java.util.List;

/** Read model for the frozen, product-scoped label history contract. */
public record ProductLabelHistory(
        String productId,
        String productDisplayName,
        List<Version> versions,
        List<Approval> approvals,
        List<AuditEvent> auditEvents
) {
    public ProductLabelHistory {
        versions = List.copyOf(versions);
        approvals = List.copyOf(approvals);
        auditEvents = List.copyOf(auditEvents);
    }

    public record Version(
            String labelVersionId,
            int versionNumber,
            String jurisdictionCode,
            String lifecycleStatus,
            String formulaVersionId,
            String ruleSetVersionId,
            LocalDateTime createdAt,
            String createdByUserId,
            String createdByDisplayName,
            LocalDateTime publishedAt,
            String publishedByUserId,
            String publishedByDisplayName,
            String publicationChannel,
            List<Declaration> declarations
    ) {
        public Version {
            declarations = List.copyOf(declarations);
        }
    }

    public record Declaration(String allergenCode, String declarationType, String source, String displayText) {}

    public record Approval(
            String approvalRecordId,
            String labelVersionId,
            String reviewTaskId,
            String decision,
            String decidedByUserId,
            String decidedByDisplayName,
            LocalDateTime decidedAt,
            String comments
    ) {}

    public record AuditEvent(
            String auditEventId,
            String eventType,
            String entityType,
            String entityId,
            LocalDateTime eventAt,
            String actorUserId,
            String actorDisplayName,
            String eventPayload
    ) {}
}
