package com.spectrace.workflow;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class S3ReadModelsMySqlTest extends MySqlIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;
    private final String prefix = "s3_read_" + UUID.randomUUID().toString().replace("-", "");
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void relationalReadFixture() {
        jdbc.update("""
                INSERT INTO change_request(change_request_id,change_request_code,change_type,status,
                    requested_at,requested_by_user_id,description,from_formula_version_id,to_formula_version_id,data_provenance_id)
                SELECT ?,?,'FORMULA','ANALYZED',NOW(),'user_label_officer','Read API fixture',
                    formula_version_id,formula_version_id,'prov_project_seed'
                FROM label_version ORDER BY label_version_id LIMIT 1
                """, prefix, prefix);
        jdbc.update("""
                INSERT INTO impact_analysis_run(impact_analysis_run_id,run_code,change_request_id,
                    idempotency_key,rule_set_version_id,status,started_at,completed_at,executed_by_user_id,data_provenance_id)
                SELECT ?,?,?,?,rule_set_version_id,'COMPLETED',NOW(),NOW(),'user_label_officer','prov_project_seed'
                FROM label_version ORDER BY label_version_id LIMIT 1
                """, prefix, prefix, prefix, prefix);
        List<Map<String,Object>> labels = jdbc.queryForList("""
                SELECT label_version_id,product_id,formula_version_id FROM label_version
                WHERE lifecycle_status='PUBLISHED' AND is_current_published='Y'
                ORDER BY product_id LIMIT 3
                """);
        assertEquals(3, labels.size(), "Fixture must bind three actual seeded product/label rows");
        for (int i = 0; i < 3; i++) {
            var label = labels.get(i);
            String id = prefix + "_" + (char)('a' + i);
            jdbc.update("""
                    INSERT INTO impact_finding(impact_finding_id,impact_analysis_run_id,product_id,
                        current_formula_version_id,proposed_formula_version_id,current_label_version_id,
                        classification,missing_allergen_codes,explanation,data_provenance_id)
                    VALUES (?,?,?,?,?,?,'REVIEW_REQUIRED',JSON_ARRAY(),'Read API fixture','prov_project_seed')
                    """, id, prefix, label.get("product_id"), label.get("formula_version_id"),
                    label.get("formula_version_id"), label.get("label_version_id"));
            jdbc.update("""
                    INSERT INTO review_task(review_task_id,impact_finding_id,product_id,current_label_version_id,
                        status,assigned_to_user_id,created_by_user_id,created_at,data_provenance_id)
                    VALUES (?,?,?, ?,?,'user_approver','user_label_officer','2099-01-01 00:00:00','prov_project_seed')
                    """, id, id, label.get("product_id"), label.get("label_version_id"), i == 1 ? "IN_REVIEW" : "OPEN");
        }
    }

    @AfterEach
    void removeOnlyThisTestFixture() {
        for (int i = 0; i < 3; i++) {
            String id = prefix + "_" + (char)('a' + i);
            jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", id);
            jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", id);
        }
        jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", prefix);
        jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", prefix);
    }

    @Test
    void actualHttpPaginationFilteringAndDetailPreserveAllBusinessAndGrantRows() throws Exception {
        var before = snapshot();
        var page = read("/api/review-tasks?limit=2&offset=0");
        assertEquals(prefix + "_a", page.get(0).get("reviewTaskId").asText());
        assertEquals(prefix + "_b", page.get(1).get("reviewTaskId").asText());
        var second = read("/api/review-tasks?limit=1&offset=1");
        assertEquals(page.get(1), second.get(0), "Stable tie ordering must not duplicate or skip the second task");
        var open = read("/api/review-tasks?limit=2&status=OPEN");
        assertEquals(prefix + "_a", open.get(0).get("reviewTaskId").asText());
        assertEquals(prefix + "_c", open.get(1).get("reviewTaskId").asText());
        var detail = read("/api/review-tasks/" + prefix + "_a");
        assertEquals(detail, page.get(0), "Collection and actual detail must expose identical binding fields");
        assertTrue(detail.get("draftLabelVersionId").isNull());
        assertTrue(detail.get("targetLabelVersionId").isNull());
        assertTrue(detail.get("decision").isNull());
        assertTrue(detail.get("resolvedAt").isNull());
        assertEquals(before, snapshot(), "GET must not write labels, tasks, approvals, publications, audit, identities or grants");
    }

    @Test
    void currentIdentityUsesDatabaseActorPermissionsWithoutInventingApprovalOrPublication() throws Exception {
        var before = snapshot();
        var current = read("/api/identity/current");
        assertEquals("user_label_officer", current.get("userId").asText());
        assertEquals("label.officer", current.get("username").asText());
        List<String> actualPermissions = jdbc.queryForList("""
                SELECT DISTINCT p.permission_code FROM user_role ur
                JOIN role_permission rp ON rp.role_id=ur.role_id
                JOIN permission p ON p.permission_id=rp.permission_id
                WHERE ur.user_id='user_label_officer' ORDER BY p.permission_code
                """, String.class);
        assertEquals(json.valueToTree(actualPermissions), current.get("permissions"));
        assertFalse(actualPermissions.contains("LABEL.APPROVE"));
        assertFalse(actualPermissions.contains("LABEL.PUBLISH"));
        assertFalse(current.has("externalSubject"));
        assertFalse(current.has("passwordHash"));
        assertEquals(before, snapshot(), "Identity read must not create users or grant permissions");
    }

    private JsonNode read(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                    + environment.getProperty("local.server.port") + path))
                    .header("X-Auth-Provider", "DEV_EXTERNAL")
                    .header("X-External-Subject", "dev-external-label-officer")
                    .GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            return json.readTree(response.body());
        }
    }

    private Map<String,List<Map<String,Object>>> snapshot() {
        var rows = new LinkedHashMap<String,List<Map<String,Object>>>();
        Map<String,String> tables = Map.of(
                "review_task", "review_task_id", "label_version", "label_version_id",
                "approval_record", "approval_record_id", "publication_record", "publication_record_id",
                "audit_event", "audit_event_id", "user_account", "user_id",
                "user_role", "user_id,role_id", "role_permission", "role_id,permission_id");
        tables.forEach((table, order) -> rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + order)));
        return rows;
    }
}
