package com.spectrace.audit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.spectrace.audit.application.ProductLabelHistory.*;

@Service
public class ProductLabelHistoryReadService {
    private final JdbcTemplate jdbc;

    public ProductLabelHistoryReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ProductLabelHistory getProductLabelHistory(String productId) {
        List<String> displayNames = jdbc.query(
                "SELECT product_description FROM product WHERE product_id = ?",
                (result, row) -> result.getString("product_description"), productId);
        if (displayNames.isEmpty()) throw new ProductLabelHistoryNotFoundException(productId);

        List<Version> versionRows = jdbc.query("""
                SELECT lv.label_version_id, lv.version_number, lv.jurisdiction_code, lv.lifecycle_status,
                       lv.formula_version_id, lv.rule_set_version_id, lv.created_at,
                       lv.created_by_user_id, creator.display_name AS created_by_display_name,
                       publication.published_at, publication.published_by_user_id,
                       publisher.display_name AS published_by_display_name, publication.publication_channel
                FROM label_version lv
                JOIN user_account creator ON creator.user_id = lv.created_by_user_id
                LEFT JOIN publication_record publication ON publication.label_version_id = lv.label_version_id
                LEFT JOIN user_account publisher ON publisher.user_id = publication.published_by_user_id
                WHERE lv.product_id = ?
                ORDER BY lv.jurisdiction_code, lv.version_number, lv.label_version_id
                """, (result, row) -> new Version(
                result.getString("label_version_id"), result.getInt("version_number"),
                result.getString("jurisdiction_code"), result.getString("lifecycle_status"),
                result.getString("formula_version_id"), result.getString("rule_set_version_id"),
                localDateTime(result.getTimestamp("created_at")), result.getString("created_by_user_id"),
                result.getString("created_by_display_name"), localDateTime(result.getTimestamp("published_at")),
                result.getString("published_by_user_id"), result.getString("published_by_display_name"),
                result.getString("publication_channel"), List.of()), productId);

        Map<String, List<Declaration>> declarations = new LinkedHashMap<>();
        jdbc.query("""
                SELECT declaration.label_version_id, allergen.allergen_code, declaration.declaration_type,
                       declaration.declaration_source, declaration.display_text
                FROM label_version version
                JOIN label_allergen_declaration declaration ON declaration.label_version_id = version.label_version_id
                JOIN allergen ON allergen.allergen_id = declaration.allergen_id
                WHERE version.product_id = ?
                ORDER BY version.jurisdiction_code, version.version_number, allergen.allergen_code,
                         declaration.declaration_type
                """, result -> {
            declarations.computeIfAbsent(result.getString("label_version_id"), ignored -> new ArrayList<>())
                    .add(new Declaration(result.getString("allergen_code"), result.getString("declaration_type"),
                            result.getString("declaration_source"), result.getString("display_text")));
        }, productId);
        List<Version> versions = versionRows.stream().map(version -> new Version(
                version.labelVersionId(), version.versionNumber(), version.jurisdictionCode(), version.lifecycleStatus(),
                version.formulaVersionId(), version.ruleSetVersionId(), version.createdAt(), version.createdByUserId(),
                version.createdByDisplayName(), version.publishedAt(), version.publishedByUserId(),
                version.publishedByDisplayName(), version.publicationChannel(),
                declarations.getOrDefault(version.labelVersionId(), List.of()))).toList();

        List<Approval> approvals = jdbc.query("""
                SELECT approval.approval_record_id, approval.label_version_id, approval.review_task_id,
                       approval.decision, approval.decided_by_user_id, decider.display_name AS decided_by_display_name,
                       approval.decided_at, approval.comments
                FROM approval_record approval
                JOIN label_version version ON version.label_version_id = approval.label_version_id
                JOIN user_account decider ON decider.user_id = approval.decided_by_user_id
                WHERE version.product_id = ?
                ORDER BY approval.decided_at, approval.approval_record_id
                """, (result, row) -> new Approval(
                result.getString("approval_record_id"), result.getString("label_version_id"),
                result.getString("review_task_id"), result.getString("decision"),
                result.getString("decided_by_user_id"), result.getString("decided_by_display_name"),
                localDateTime(result.getTimestamp("decided_at")), result.getString("comments")), productId);

        List<AuditEvent> auditEvents = jdbc.query("""
                SELECT event.audit_event_id, event.event_type, event.entity_type, event.entity_id,
                       event.event_at, event.actor_user_id, actor.display_name AS actor_display_name,
                       CAST(event.event_payload AS CHAR) AS event_payload
                FROM audit_event event
                JOIN user_account actor ON actor.user_id = event.actor_user_id
                WHERE (event.entity_type = 'LABEL_VERSION'
                       AND event.entity_id IN (
                         SELECT label_version_id FROM label_version WHERE product_id = ?
                       ))
                   OR (event.entity_type = 'REVIEW_TASK'
                       AND event.entity_id IN (
                         SELECT review_task_id FROM review_task WHERE product_id = ?
                       ))
                ORDER BY event.event_at, event.audit_event_id
                """, (result, row) -> new AuditEvent(
                result.getString("audit_event_id"), result.getString("event_type"),
                result.getString("entity_type"), result.getString("entity_id"),
                localDateTime(result.getTimestamp("event_at")), result.getString("actor_user_id"),
                result.getString("actor_display_name"), result.getString("event_payload")), productId, productId);

        return new ProductLabelHistory(productId, displayNames.getFirst(), versions, approvals, auditEvents);
    }

    private static java.time.LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
