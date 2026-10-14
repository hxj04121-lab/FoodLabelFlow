package com.spectrace.audit.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLabelHistoryServiceTest {
    @Test
    void mapsVersionDeclarationsApprovalsAndAuditEventsWithoutChangingHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(row("product_id", "product-1", "product_description", "Chocolate product")),
                List.of(row("label_version_id", "label-1", "version_number", 1,
                        "jurisdiction_code", "US", "lifecycle_status", "PUBLISHED",
                        "formula_version_id", "formula-1", "rule_set_version_id", "rules-1",
                        "created_at", "2026-10-01T09:00:00Z", "created_by_user_id", "maker-1",
                        "created_by_name", "Maker", "published_at", null,
                        "published_by_user_id", null, "published_by_name", null,
                        "publication_channel", null)),
                List.of(row("label_version_id", "label-1", "allergen_code", "SOY",
                        "declaration_type", "CONTAINS", "declaration_source", "USER_ENTERED",
                        "display_text", "Contains soy")),
                List.of(row("approval_record_id", "approval-1", "label_version_id", "label-1",
                        "review_task_id", "task-1", "decision", "APPROVE",
                        "decided_by_user_id", "checker-1", "decided_by_name", "Checker",
                        "decided_at", "2026-10-01T10:00:00Z", "comments", "Reviewed")),
                List.of(row("audit_event_id", "event-1", "event_type", "LABEL_PUBLISHED",
                        "entity_type", "LABEL_VERSION", "entity_id", "label-1",
                        "event_at", "2026-10-01T10:00:01Z", "actor_user_id", "publisher-1",
                        "actor_name", "Publisher", "event_payload", "{}")));

        var history = new ProductLabelHistoryService(jdbc).get("product-1");

        assertThat(history.productDisplayName()).isEqualTo("Chocolate product");
        assertThat(history.versions()).containsExactly(new ProductLabelHistoryService.LabelVersion(
                "label-1", 1, "US", "PUBLISHED", "formula-1", "rules-1", "2026-10-01T09:00:00Z",
                "maker-1", "Maker", null, null, null, null,
                List.of(new ProductLabelHistoryService.Declaration("SOY", "CONTAINS", "USER_ENTERED", "Contains soy"))));
        assertThat(history.approvals()).containsExactly(new ProductLabelHistoryService.ApprovalDecision(
                "approval-1", "label-1", "task-1", "APPROVE", "checker-1", "Checker",
                "2026-10-01T10:00:00Z", "Reviewed"));
        assertThat(history.auditEvents()).containsExactly(new ProductLabelHistoryService.AuditEvent(
                "event-1", "LABEL_PUBLISHED", "LABEL_VERSION", "label-1", "2026-10-01T10:00:01Z",
                "publisher-1", "Publisher", "{}"));
        verify(jdbc, org.mockito.Mockito.times(5)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void missingProductFailsBeforeReadingRelatedHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> new ProductLabelHistoryService(jdbc).get("missing-product"))
                .isInstanceOfSatisfying(AuditHistoryFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(404);
                    assertThat(failure.code()).isEqualTo("PRODUCT_NOT_FOUND");
                    assertThat(failure.getMessage()).isEqualTo("Product not found");
                });

        verify(jdbc).queryForList(anyString(), any(Object[].class));
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
