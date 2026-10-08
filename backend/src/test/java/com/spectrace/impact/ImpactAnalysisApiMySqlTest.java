package com.spectrace.impact;

import com.spectrace.audit.application.AuditApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * SCRUM-79: the trigger and query operations over real HTTP and committed MySQL rows, with
 * no test-managed transaction. Owns its container because the Spec V2 adoption fixture commits.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/fixtures/s3-soy-spec-v2-adoption.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO ingredient_specification_version(specification_version_id, supplier_material_id, "
                + "version_number, lifecycle_status, effective_date, released_at, created_by_user_id, "
                + "data_provenance_id) VALUES ('spec_wheat_flour_v2', 'mat_wheat_flour', 2, 'RELEASED', "
                + "'2026-09-01', '2026-09-01 09:00:00', 'user_admin', 'prov_project_seed')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ImpactAnalysisApiMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CHANGE_MANAGER = "dev-external-change-manager";
    private static final String LABEL_OFFICER = "dev-external-label-officer";
    private static final String AUDITOR = "dev-external-auditor";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String CR = "cr-scrum79-soy";
    private static final String TRIGGER = "/api/v1/change-requests/" + CR + "/impact-analyses";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("scrum79").withPassword("scrum79_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditApplicationService audit;

    @BeforeEach
    void freshChangeRequests() {
        jdbc.update("DELETE FROM review_task");
        jdbc.update("DELETE FROM impact_finding");
        jdbc.update("DELETE FROM impact_analysis_run");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM change_request");
        insertChangeRequest(CR, "spec_chocolate_v1", "spec_chocolate_v2");
    }

    @Test
    void aFirstTriggerReturns201WithTheContractShapeAndTheQueryReadsItBack() throws Exception {
        var created = trigger(TRIGGER, RULE_SET, CHANGE_MANAGER);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.body());
        String id = body.get("impactAnalysisId").stringValue();
        assertThat(created.headers().firstValue("Location")).contains("/api/v1/impact-analyses/" + id);
        assertThat(names(body)).containsExactly("impactAnalysisId", "changeRequestId", "ruleSetVersionId", "status",
                "completedAt", "relevantProductCount", "noActionCount", "reviewRequiredCount", "findings");
        assertThat(body.get("changeRequestId").stringValue()).isEqualTo(CR);
        assertThat(body.get("ruleSetVersionId").stringValue()).isEqualTo(RULE_SET);
        assertThat(body.get("status").stringValue()).isEqualTo("COMPLETED");
        assertThat(body.get("completedAt").stringValue()).endsWith("Z");
        assertThat(body.get("relevantProductCount").intValue()).isEqualTo(40);
        assertThat(body.get("noActionCount").intValue()).isEqualTo(20);
        assertThat(body.get("reviewRequiredCount").intValue()).isEqualTo(20);

        List<String> productIds = new ArrayList<>();
        for (JsonNode finding : body.get("findings")) {
            productIds.add(finding.get("productId").stringValue());
            if (finding.get("outcome").stringValue().equals("NO_ACTION")) {
                assertThat(names(finding)).containsExactly("impactFindingId", "productId", "outcome",
                        "currentFormulaVersionId", "proposedFormulaVersionId", "currentLabelVersionId",
                        "missingAllergenCodes", "explanation");
                assertThat(finding.get("missingAllergenCodes")).isEmpty();
            } else {
                assertThat(names(finding)).containsExactly("impactFindingId", "productId", "outcome",
                        "currentFormulaVersionId", "proposedFormulaVersionId", "currentLabelVersionId",
                        "missingAllergenCodes", "explanation", "reviewTask");
                assertThat(finding.get("missingAllergenCodes").get(0).stringValue()).isEqualTo("SOY");
                JsonNode task = finding.get("reviewTask");
                assertThat(names(task)).containsExactly("reviewTaskId", "impactFindingId", "productId",
                        "currentFormulaVersionId", "currentLabelVersionId", "draftLabelVersionId");
                assertThat(task.get("impactFindingId")).isEqualTo(finding.get("impactFindingId"));
                assertThat(task.get("currentFormulaVersionId")).isEqualTo(finding.get("currentFormulaVersionId"));
                assertThat(task.get("draftLabelVersionId").isNull()).isTrue();
            }
            assertThat(finding.get("proposedFormulaVersionId").stringValue())
                    .isEqualTo(finding.get("currentFormulaVersionId").stringValue() + "n1soy");
        }
        assertThat(productIds).isSorted();

        assertThat(jdbc.queryForObject("SELECT status FROM change_request WHERE change_request_id = ?",
                String.class, CR)).isEqualTo("ANALYZED");
        assertThat(jdbc.queryForList("SELECT DISTINCT status, assigned_to_user_id, created_by_user_id "
                + "FROM review_task")).containsExactly(Map.of(
                "status", "OPEN", "assigned_to_user_id", "user_label_officer",
                "created_by_user_id", "user_change_manager"));
        assertThat(count("review_task WHERE draft_label_version_id IS NOT NULL")).isZero();
        assertThat(jdbc.queryForList("SELECT event_type, entity_id, actor_user_id, "
                + "JSON_UNQUOTE(JSON_EXTRACT(event_payload, '$.changeRequestId')) AS change_request_id, "
                + "JSON_UNQUOTE(JSON_EXTRACT(event_payload, '$.outcome')) AS outcome FROM audit_event"))
                .containsExactly(Map.of("event_type", "IMPACT_ANALYSIS", "entity_id", id,
                        "actor_user_id", "user_change_manager", "change_request_id", CR, "outcome", "COMPLETED"));

        var read = request("GET", "/api/v1/impact-analyses/" + id, null, AUDITOR);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(body);
    }

    @Test
    void replayingTheSameRuleSetReturns200AndTheSameAnalysisWithoutNewRows() throws Exception {
        JsonNode first = JSON.readTree(trigger(TRIGGER, RULE_SET, CHANGE_MANAGER).body());
        Map<String, Object> before = rowCounts();

        var replay = trigger(TRIGGER, RULE_SET, CHANGE_MANAGER);

        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(JSON.readTree(replay.body())).isEqualTo(first);
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void replayingAnotherRuleSetIsAConflict() throws Exception {
        trigger(TRIGGER, RULE_SET, CHANGE_MANAGER);
        Map<String, Object> before = rowCounts();

        assertError(trigger(TRIGGER, "ruleset_us_falcpa_demo_v2", CHANGE_MANAGER), 409, "DATA_CONFLICT");
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void concurrentTriggersCreateExactlyOneAnalysis() throws Exception {
        var ready = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<HttpResponse<String>>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(executor.submit(() -> {
                    ready.await();
                    return trigger(TRIGGER, RULE_SET, CHANGE_MANAGER);
                }));
            }
            ready.countDown();
            for (var call : calls) {
                statuses.add(call.get().statusCode());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 201);
        assertThat(rowCounts()).isEqualTo(Map.of("runs", 1L, "findings", 40L, "tasks", 20L, "audits", 1L));
    }

    @Test
    void aFailedAuditRollsBackTheRunFindingsTasksAndStatus() throws Exception {
        AuditApplicationService target = AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("audit store unavailable");
        }).when(target).recordImpactEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        var response = trigger(TRIGGER, RULE_SET, CHANGE_MANAGER);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("audit store unavailable");
        assertNothingWritten();
    }

    @Test
    void identityAndTheImpactRunPermissionAreRequired() throws Exception {
        assertError(trigger(TRIGGER, RULE_SET, null), 401, "AUTHENTICATION_REQUIRED");
        assertError(trigger(TRIGGER, RULE_SET, "unknown-subject"), 401, "AUTHENTICATION_REQUIRED");
        assertError(trigger(TRIGGER, RULE_SET, LABEL_OFFICER), 403, "AUTHORIZATION_DENIED");
        assertError(request("GET", "/api/v1/impact-analyses/any", null, null), 401, "AUTHENTICATION_REQUIRED");
        assertNothingWritten();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "{}", "{\"ruleSetVersionId\":\" \"}", "{\"ruleSetVersionId\":7}",
            "{\"ruleSetVersionId\":\"ruleset_us_falcpa_demo_v1\",\"actor\":\"user_admin\"}",
            "{\"ruleSetVersionId\":\"a\",\"ruleSetVersionId\":\"b\"}"})
    void malformedTriggerBodiesAreInvalid(String body) throws Exception {
        assertError(request("POST", TRIGGER, body, CHANGE_MANAGER), 400, "INVALID_REQUEST");
        assertNothingWritten();
    }

    @Test
    void unknownPathResourcesAre404AndDomainPreconditionsAre422() throws Exception {
        assertError(trigger("/api/v1/change-requests/cr-missing/impact-analyses", RULE_SET, CHANGE_MANAGER),
                404, "RESOURCE_NOT_FOUND");
        assertError(request("GET", "/api/v1/impact-analyses/missing", null, AUDITOR), 404, "RESOURCE_NOT_FOUND");
        assertError(trigger(TRIGGER, "ruleset_us_falcpa_demo_v2", CHANGE_MANAGER), 422, "RULE_SET_NOT_ACTIVE");
        assertError(trigger(TRIGGER, "ruleset_missing", CHANGE_MANAGER), 422, "RULE_SET_NOT_ACTIVE");

        insertChangeRequest("cr-scrum79-wheat", "spec_wheat_flour_v1", "spec_wheat_flour_v2");
        assertError(trigger("/api/v1/change-requests/cr-scrum79-wheat/impact-analyses", RULE_SET, CHANGE_MANAGER),
                422, "FORMULA_ADOPTION_PENDING");
        assertNothingWritten();
    }

    @Test
    void aCancelledChangeRequestIsAConflict() throws Exception {
        jdbc.update("UPDATE change_request SET status = 'CANCELLED' WHERE change_request_id = ?", CR);

        assertError(trigger(TRIGGER, RULE_SET, CHANGE_MANAGER), 409, "DATA_CONFLICT");
        assertThat(rowCounts()).isEqualTo(Map.of("runs", 0L, "findings", 0L, "tasks", 0L, "audits", 0L));
    }

    private void insertChangeRequest(String id, String from, String to) {
        jdbc.update("""
                INSERT INTO change_request(change_request_id, change_request_code, change_type, status, requested_at,
                    requested_by_user_id, description, from_specification_version_id, to_specification_version_id,
                    data_provenance_id)
                VALUES (?, ?, 'INGREDIENT_SPEC', 'SUBMITTED', '2026-10-01 09:00:00', 'user_change_manager',
                    'S3 scenario change', ?, ?, 'prov_scenario_input')
                """, id, "CR-" + id, from, to);
    }

    private Map<String, Object> rowCounts() {
        return Map.of(
                "runs", count("impact_analysis_run"), "findings", count("impact_finding"),
                "tasks", count("review_task"), "audits", count("audit_event"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private void assertNothingWritten() {
        assertThat(rowCounts()).isEqualTo(Map.of("runs", 0L, "findings", 0L, "tasks", 0L, "audits", 0L));
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM change_request", String.class))
                .containsExactly("SUBMITTED");
    }

    private static List<String> names(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }

    private HttpResponse<String> trigger(String path, String ruleSetVersionId, String subject) throws Exception {
        return request("POST", path, "{\"ruleSetVersionId\":\"" + ruleSetVersionId + "\"}", subject);
    }

    private HttpResponse<String> request(String method, String path, String body, String subject) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (subject != null) {
            request.header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        JsonNode error = JSON.readTree(response.body());
        assertThat(error.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("code", "message", "traceId", "evidenceId");
        assertThat(error.get("code").stringValue()).isEqualTo(code);
        assertThat(error.get("message").stringValue()).isNotBlank();
    }
}
