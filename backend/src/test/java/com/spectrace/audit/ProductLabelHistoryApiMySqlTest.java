package com.spectrace.audit;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class ProductLabelHistoryApiMySqlTest extends MySqlIntegrationTestSupport {
    private static final String PRODUCT_ID = "prod_usda_1106285";
    private static final String OTHER_PRODUCT_ID = "prod_usda_1106963";
    private static final String AUDITOR = "dev-external-auditor";
    private static final String MAKER = "dev-external-label-officer";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TARGET = "history_target";
    private static final String OTHER = "history_other";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedIsolatedHistoryRows() {
        cleanFixture(TARGET);
        cleanFixture(OTHER);
        seedProductHistory(TARGET, PRODUCT_ID, "formula_1106285_v1", "label_1106285_v1", "all_soy");
        seedProductHistory(OTHER, OTHER_PRODUCT_ID, "formula_1106963_v1", "label_1106963_v1", "all_milk");
    }

    @AfterEach
    void removeOnlyFixtureRows() {
        cleanFixture(TARGET);
        cleanFixture(OTHER);
    }

    @Test
    void auditorReadsOrderedProductScopedVersionsDeclarationsApprovalsPublicationAndAudit() throws Exception {
        var response = request("/api/audit/products/" + PRODUCT_ID + "/label-history", AUDITOR);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);

        JsonNode body = JSON.readTree(response.body());
        assertThat(names(body)).containsExactly("productId", "productDisplayName", "versions", "approvals", "auditEvents");
        assertThat(body.get("productId").stringValue()).isEqualTo(PRODUCT_ID);
        assertThat(body.get("productDisplayName").stringValue()).isEqualTo(jdbc.queryForObject(
                "SELECT product_description FROM product WHERE product_id=?", String.class, PRODUCT_ID));

        List<JsonNode> versions = nodes(body.get("versions"));
        assertThat(versions).extracting(version -> version.get("labelVersionId").stringValue())
                .containsExactly("label_1106285_v1", TARGET + "_label_v2");
        assertThat(versions).extracting(version -> version.get("versionNumber").intValue())
                .containsExactly(1, 2);
        JsonNode secondVersion = versions.get(1);
        assertThat(names(secondVersion)).containsExactly("labelVersionId", "versionNumber", "jurisdictionCode",
                "lifecycleStatus", "formulaVersionId", "ruleSetVersionId", "createdAt", "createdByUserId",
                "createdByDisplayName", "publishedAt", "publishedByUserId", "publishedByDisplayName",
                "publicationChannel", "declarations");
        assertThat(secondVersion.get("createdAt").stringValue()).isEqualTo("2026-10-02T10:00:00");
        assertThat(secondVersion.get("createdByUserId").stringValue()).isEqualTo("user_label_officer");
        assertThat(secondVersion.get("createdByDisplayName").stringValue()).isEqualTo("Demo Label Officer");
        assertThat(secondVersion.get("declarations").size()).isEqualTo(1);
        JsonNode declaration = secondVersion.get("declarations").get(0);
        assertThat(names(declaration)).containsExactly("allergenCode", "declarationType", "source", "displayText");
        assertThat(declaration.get("allergenCode").stringValue()).isEqualTo("SOY");
        assertThat(declaration.get("declarationType").stringValue()).isEqualTo("CONTAINS");
        assertThat(declaration.get("source").stringValue()).isEqualTo("USER_ENTERED");
        assertThat(declaration.get("displayText").stringValue()).isEqualTo("Contains: Soy (history fixture)");

        assertThat(secondVersion.get("publishedAt").stringValue()).isEqualTo("2026-10-02T12:00:00");
        assertThat(secondVersion.get("publishedByUserId").stringValue()).isEqualTo("user_publisher");
        assertThat(secondVersion.get("publishedByDisplayName").stringValue()).isEqualTo("Demo Publisher");
        assertThat(secondVersion.get("publicationChannel").stringValue()).isEqualTo("HISTORY_FIXTURE");

        List<JsonNode> approvals = nodes(body.get("approvals"));
        assertThat(approvals).hasSize(1);
        assertThat(names(approvals.getFirst())).containsExactly("approvalRecordId", "labelVersionId", "reviewTaskId",
                "decision", "decidedByUserId", "decidedByDisplayName", "decidedAt", "comments");
        assertThat(approvals.getFirst().get("approvalRecordId").stringValue()).isEqualTo(TARGET + "_approval");
        assertThat(approvals.getFirst().get("labelVersionId").stringValue()).isEqualTo(TARGET + "_label_v2");
        assertThat(approvals.getFirst().get("decision").stringValue()).isEqualTo("APPROVE");
        assertThat(approvals.getFirst().get("decidedAt").stringValue()).isEqualTo("2026-10-02T11:00:00");
        assertThat(approvals.getFirst().get("decidedByDisplayName").stringValue()).isEqualTo("Demo Approver");

        List<JsonNode> events = nodes(body.get("auditEvents"));
        assertThat(names(events.getFirst())).containsExactly("auditEventId", "eventType", "entityType", "entityId",
                "eventAt", "actorUserId", "actorDisplayName", "eventPayload");
        assertThat(events).extracting(event -> event.get("auditEventId").stringValue())
                .containsExactly(TARGET + "_event_label", TARGET + "_event_review", TARGET + "_event_publish");
        assertThat(events).extracting(event -> event.get("eventType").stringValue())
                .containsExactly("LABEL_VERSION_CREATED", "LABEL_APPROVED", "LABEL_PUBLISHED");
        assertThat(events).extracting(event -> event.get("eventAt").stringValue())
                .containsExactly("2026-10-02T10:30:00", "2026-10-02T11:00:00", "2026-10-02T12:00:00");
        assertThat(events).extracting(event -> event.get("actorDisplayName").stringValue())
                .containsExactly("Demo Label Officer", "Demo Approver", "Demo Publisher");

        String serialized = body.toString();
        assertThat(serialized).doesNotContain(OTHER + "_label_v2", OTHER + "_approval", OTHER + "_event_label",
                OTHER + "_event_review", OTHER + "_event_publish");
    }

    @Test
    void nonAuditorAndAnonymousCallerCannotReadHistoryAndUnknownProductsReturn404() throws Exception {
        var denied = request("/api/audit/products/" + PRODUCT_ID + "/label-history", MAKER);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(denied.body()).get("code").stringValue()).isEqualTo("AUTHORIZATION_DENIED");

        var anonymous = request("/api/audit/products/" + PRODUCT_ID + "/label-history", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(anonymous.body()).get("code").stringValue()).isEqualTo("AUTHENTICATION_REQUIRED");

        var missing = request("/api/audit/products/missing-product/label-history", AUDITOR);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(missing.body()).get("code").stringValue()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    private void seedProductHistory(String prefix, String productId, String formulaVersionId,
                                    String currentLabelVersionId, String allergenId) {
        String labelId = prefix + "_label_v2";
        String changeRequestId = prefix + "_change_request";
        String runId = prefix + "_impact_run";
        String findingId = prefix + "_impact_finding";
        String reviewTaskId = prefix + "_review_task";

        jdbc.update("""
                INSERT INTO label_version (label_version_id, product_id, formula_version_id, rule_set_version_id,
                  jurisdiction_code, version_number, raw_ingredient_text, lifecycle_status, is_current_published,
                  created_by_user_id, created_at, data_provenance_id)
                VALUES (?, ?, ?, 'ruleset_us_falcpa_demo_v1', 'US', 2, 'isolated history fixture', 'PUBLISHED', 'N',
                  'user_label_officer', '2026-10-02 10:00:00', 'prov_project_seed')
                """, labelId, productId, formulaVersionId);
        jdbc.update("""
                INSERT INTO label_allergen_declaration (label_allergen_declaration_id, label_version_id, allergen_id,
                  declaration_type, declaration_source, display_text, data_provenance_id)
                VALUES (?, ?, ?, 'CONTAINS', 'USER_ENTERED', ?, 'prov_project_seed')
                """, prefix + "_declaration", labelId, allergenId, "Contains: " + allergenName(allergenId) + " (history fixture)");
        jdbc.update("""
                INSERT INTO change_request (change_request_id, change_request_code, change_type, status, requested_at,
                  requested_by_user_id, description, from_rule_set_version_id, to_rule_set_version_id, data_provenance_id)
                VALUES (?, ?, 'RULE_SET', 'COMPLETED', '2026-10-02 09:00:00', 'user_admin',
                  'Isolated history endpoint fixture', 'ruleset_us_falcpa_demo_v1', 'ruleset_us_falcpa_demo_v2', 'prov_project_seed')
                """, changeRequestId, prefix + "_change_request_code");
        jdbc.update("""
                INSERT INTO impact_analysis_run (impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                  rule_set_version_id, status, started_at, completed_at, executed_by_user_id, data_provenance_id)
                VALUES (?, ?, ?, ?, 'ruleset_us_falcpa_demo_v2', 'COMPLETED', '2026-10-02 09:10:00',
                  '2026-10-02 09:11:00', 'user_admin', 'prov_project_seed')
                """, runId, prefix + "_impact_run_code", changeRequestId, prefix + "_idempotency_key");
        jdbc.update("""
                INSERT INTO impact_finding (impact_finding_id, impact_analysis_run_id, product_id,
                  current_formula_version_id, proposed_formula_version_id, current_label_version_id,
                  classification, missing_allergen_codes, explanation, data_provenance_id)
                VALUES (?, ?, ?, ?, NULL, ?, 'REVIEW_REQUIRED', JSON_ARRAY(?), 'Isolated history endpoint fixture', 'prov_project_seed')
                """, findingId, runId, productId, formulaVersionId, currentLabelVersionId, allergenCode(allergenId));
        jdbc.update("""
                INSERT INTO review_task (review_task_id, impact_finding_id, product_id, current_label_version_id,
                  draft_label_version_id, status, assigned_to_user_id, created_by_user_id, created_at, data_provenance_id)
                VALUES (?, ?, ?, ?, ?, 'CLOSED', 'user_approver', 'user_admin', '2026-10-02 10:30:00', 'prov_project_seed')
                """, reviewTaskId, findingId, productId, currentLabelVersionId, labelId);
        jdbc.update("""
                INSERT INTO approval_record (approval_record_id, label_version_id, review_task_id, decision,
                  decided_by_user_id, decided_at, comments, data_provenance_id)
                VALUES (?, ?, ?, 'APPROVE', 'user_approver', '2026-10-02 11:00:00',
                  'Isolated history fixture approval', 'prov_project_seed')
                """, prefix + "_approval", labelId, reviewTaskId);
        jdbc.update("""
                INSERT INTO publication_record (publication_record_id, label_version_id, published_by_user_id,
                  published_at, publication_channel, data_provenance_id)
                VALUES (?, ?, 'user_publisher', '2026-10-02 12:00:00', 'HISTORY_FIXTURE', 'prov_project_seed')
                """, prefix + "_publication", labelId);
        insertAuditEvent(prefix + "_event_label", "LABEL_VERSION_CREATED", "LABEL_VERSION", labelId,
                "user_label_officer", "2026-10-02 10:30:00");
        insertAuditEvent(prefix + "_event_review", "LABEL_APPROVED", "REVIEW_TASK", reviewTaskId,
                "user_approver", "2026-10-02 11:00:00");
        insertAuditEvent(prefix + "_event_publish", "LABEL_PUBLISHED", "LABEL_VERSION", labelId,
                "user_publisher", "2026-10-02 12:00:00");
    }

    private void insertAuditEvent(String id, String eventType, String entityType, String entityId, String actorId,
                                  String time) {
        jdbc.update("""
                INSERT INTO audit_event (audit_event_id, event_type, entity_type, entity_id, event_at, actor_user_id,
                  before_value, after_value, event_payload, correlation_id, data_provenance_id)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, JSON_OBJECT('fixture', TRUE), ?, 'prov_project_seed')
                """, id, eventType, entityType, entityId, time, actorId, id);
    }

    private void cleanFixture(String prefix) {
        jdbc.update("DELETE FROM audit_event WHERE audit_event_id IN (?, ?, ?)",
                prefix + "_event_label", prefix + "_event_review", prefix + "_event_publish");
        jdbc.update("DELETE FROM publication_record WHERE publication_record_id = ?", prefix + "_publication");
        jdbc.update("DELETE FROM approval_record WHERE approval_record_id = ?", prefix + "_approval");
        jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", prefix + "_review_task");
        jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", prefix + "_impact_finding");
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", prefix + "_impact_run");
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", prefix + "_change_request");
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_allergen_declaration_id = ?", prefix + "_declaration");
        jdbc.update("DELETE FROM label_version WHERE label_version_id = ?", prefix + "_label_v2");
    }

    private HttpResponse<String> request(String path, String subject) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (subject != null) {
            request.header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject);
        }
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(
                request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> names(JsonNode value) {
        return value.properties().stream().map(Map.Entry::getKey).toList();
    }

    private static List<JsonNode> nodes(JsonNode value) {
        var result = new java.util.ArrayList<JsonNode>();
        value.forEach(result::add);
        return result;
    }

    private static String allergenCode(String allergenId) {
        return allergenId.equals("all_soy") ? "SOY" : "MILK";
    }

    private static String allergenName(String allergenId) {
        return allergenId.equals("all_soy") ? "Soy" : "Milk";
    }
}
