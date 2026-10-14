package com.spectrace.audit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductLabelHistoryService {
    private final JdbcTemplate jdbc;

    public ProductLabelHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ProductLabelHistory get(String productId) {
        var products = jdbc.queryForList("""
                SELECT product_id, product_description FROM product WHERE product_id = ?
                """, productId);
        if (products.isEmpty()) throw new AuditHistoryFailure(404, "PRODUCT_NOT_FOUND", "Product not found");
        String displayName = (String) products.getFirst().get("product_description");

        var versions = jdbc.queryForList("""
                SELECT lv.label_version_id, lv.version_number, lv.jurisdiction_code,
                       lv.lifecycle_status, lv.formula_version_id, lv.rule_set_version_id,
                       lv.created_at, lv.created_by_user_id, creator.display_name AS created_by_name,
                       pub.published_at, pub.published_by_user_id,
                       publisher.display_name AS published_by_name, pub.publication_channel
                FROM label_version lv
                JOIN user_account creator ON creator.user_id = lv.created_by_user_id
                LEFT JOIN publication_record pub ON pub.label_version_id = lv.label_version_id
                LEFT JOIN user_account publisher ON publisher.user_id = pub.published_by_user_id
                WHERE lv.product_id = ?
                ORDER BY lv.version_number DESC, lv.label_version_id
                """, productId);
        var declarations = jdbc.queryForList("""
                SELECT d.label_version_id, a.allergen_code, d.declaration_type,
                       d.declaration_source, d.display_text
                FROM label_allergen_declaration d
                JOIN allergen a ON a.allergen_id = d.allergen_id
                JOIN label_version lv ON lv.label_version_id = d.label_version_id
                WHERE lv.product_id = ?
                ORDER BY lv.version_number DESC, a.allergen_code, d.declaration_type
                """, productId);
        Map<String, List<Declaration>> declarationsByLabel = new LinkedHashMap<>();
        for (var row : declarations) {
            declarationsByLabel.computeIfAbsent((String) row.get("label_version_id"), ignored -> new ArrayList<>())
                    .add(new Declaration((String) row.get("allergen_code"), (String) row.get("declaration_type"),
                            (String) row.get("declaration_source"), (String) row.get("display_text")));
        }
        List<LabelVersion> labelVersions = versions.stream().map(row -> new LabelVersion(
                (String) row.get("label_version_id"), ((Number) row.get("version_number")).intValue(),
                (String) row.get("jurisdiction_code"), (String) row.get("lifecycle_status"),
                (String) row.get("formula_version_id"), (String) row.get("rule_set_version_id"),
                String.valueOf(row.get("created_at")), (String) row.get("created_by_user_id"),
                (String) row.get("created_by_name"), nullableString(row.get("published_at")),
                (String) row.get("published_by_user_id"), (String) row.get("published_by_name"),
                (String) row.get("publication_channel"),
                declarationsByLabel.getOrDefault((String) row.get("label_version_id"), List.of())))
                .toList();

        var approvals = jdbc.queryForList("""
                SELECT ar.approval_record_id, ar.label_version_id, ar.review_task_id,
                       ar.decision, ar.decided_by_user_id, reviewer.display_name AS decided_by_name,
                       ar.decided_at, ar.comments
                FROM approval_record ar
                JOIN label_version lv ON lv.label_version_id = ar.label_version_id
                JOIN user_account reviewer ON reviewer.user_id = ar.decided_by_user_id
                WHERE lv.product_id = ?
                ORDER BY ar.decided_at DESC, ar.approval_record_id
                """, productId).stream().map(row -> new ApprovalDecision(
                (String) row.get("approval_record_id"), (String) row.get("label_version_id"),
                (String) row.get("review_task_id"), (String) row.get("decision"),
                (String) row.get("decided_by_user_id"), (String) row.get("decided_by_name"),
                String.valueOf(row.get("decided_at")), (String) row.get("comments"))).toList();

        var auditEvents = jdbc.queryForList("""
                SELECT ae.audit_event_id, ae.event_type, ae.entity_type, ae.entity_id,
                       ae.event_at, ae.actor_user_id, actor.display_name AS actor_name,
                       CAST(ae.event_payload AS CHAR) AS event_payload
                FROM audit_event ae
                JOIN user_account actor ON actor.user_id = ae.actor_user_id
                WHERE (ae.entity_id IN (SELECT lv.label_version_id FROM label_version lv WHERE lv.product_id = ?)
                       AND ae.entity_type IN ('LABEL_VERSION', 'LABEL'))
                   OR (ae.entity_type = 'REVIEW_TASK'
                       AND ae.entity_id IN (SELECT rt.review_task_id FROM review_task rt WHERE rt.product_id = ?))
                ORDER BY ae.event_at DESC, ae.audit_event_id
                """, productId, productId).stream().map(row -> new AuditEvent(
                (String) row.get("audit_event_id"), (String) row.get("event_type"),
                (String) row.get("entity_type"), (String) row.get("entity_id"),
                String.valueOf(row.get("event_at")), (String) row.get("actor_user_id"),
                (String) row.get("actor_name"), (String) row.get("event_payload"))).toList();

        return new ProductLabelHistory(productId, displayName, labelVersions, approvals, auditEvents);
    }

    private static String nullableString(Object value) { return value == null ? null : String.valueOf(value); }

    public record ProductLabelHistory(String productId, String productDisplayName, List<LabelVersion> versions,
                                      List<ApprovalDecision> approvals, List<AuditEvent> auditEvents) {
        public ProductLabelHistory {
            versions = List.copyOf(versions);
            approvals = List.copyOf(approvals);
            auditEvents = List.copyOf(auditEvents);
        }
    }
    public record LabelVersion(String labelVersionId, int versionNumber, String jurisdictionCode,
                               String lifecycleStatus, String formulaVersionId, String ruleSetVersionId,
                               String createdAt, String createdByUserId, String createdByDisplayName,
                               String publishedAt, String publishedByUserId, String publishedByDisplayName,
                               String publicationChannel, List<Declaration> declarations) {
        public LabelVersion { declarations = List.copyOf(declarations); }
    }
    public record Declaration(String allergenCode, String declarationType, String source, String displayText) { }
    public record ApprovalDecision(String approvalRecordId, String labelVersionId, String reviewTaskId,
                                   String decision, String decidedByUserId, String decidedByDisplayName,
                                   String decidedAt, String comments) { }
    public record AuditEvent(String auditEventId, String eventType, String entityType, String entityId,
                             String eventAt, String actorUserId, String actorDisplayName, String eventPayload) { }
}
