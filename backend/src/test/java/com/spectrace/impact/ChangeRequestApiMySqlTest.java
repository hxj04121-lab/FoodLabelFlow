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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** SCRUM-76: real HTTP, committed MySQL rows and same-transaction audit, with no test-managed transaction. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(statements = {
        "INSERT INTO supplier_material(supplier_material_id, supplier_id, ingredient_id, material_code, "
                + "material_name, material_description, data_provenance_id) VALUES ('mat_scrum76_base', "
                + "'sup_chocolate_demo', 'ing_chocolate', 'SCRUM76_BASE', 'SCRUM-76 base', "
                + "'Change-request HTTP fixture', 'prov_project_seed')",
        "INSERT INTO ingredient_specification_version(specification_version_id, supplier_material_id, "
                + "version_number, lifecycle_status, effective_date, released_at, created_by_user_id, "
                + "data_provenance_id) VALUES "
                + "('spec_scrum76_v1', 'mat_scrum76_base', 1, 'RETIRED', '2026-01-01', '2026-01-01 09:00:00', "
                + "'user_admin', 'prov_project_seed'), "
                + "('spec_scrum76_v2', 'mat_scrum76_base', 2, 'RELEASED', '2026-09-01', '2026-09-01 09:00:00', "
                + "'user_admin', 'prov_project_seed'), "
                + "('spec_scrum76_v3', 'mat_scrum76_base', 3, 'DRAFT', '2026-09-01', NULL, "
                + "'user_admin', 'prov_project_seed'), "
                + "('spec_scrum76_v4', 'mat_scrum76_base', 4, 'RELEASED', '2099-01-01', '2026-09-01 09:00:00', "
                + "'user_admin', 'prov_project_seed')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChangeRequestApiMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PATH = "/api/v1/change-requests";
    private static final String CHANGE_MANAGER = "dev-external-change-manager";
    private static final String LABEL_OFFICER = "dev-external-label-officer";
    private static final String AUDITOR = "dev-external-auditor";
    private static final String MATERIAL = "mat_scrum76_base";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("scrum76").withPassword("scrum76_password");

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
    void clearOutputs() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM change_request");
    }

    @Test
    void createsAChangeRequestThatAnotherActiveUserCanReadBack() throws Exception {
        var created = create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.body());
        String id = body.get("changeRequestId").stringValue();
        assertThat(created.headers().firstValue("Location")).contains(PATH + "/" + id);
        assertThat(body.properties().stream().map(Map.Entry::getKey).toList()).containsExactly(
                "changeRequestId", "changeType", "supplierMaterialId", "previousSpecificationVersionId",
                "targetSpecificationVersionId", "status", "createdAt");
        assertThat(body.get("changeType").stringValue()).isEqualTo("INGREDIENT_SPEC");
        assertThat(body.get("supplierMaterialId").stringValue()).isEqualTo(MATERIAL);
        assertThat(body.get("previousSpecificationVersionId").stringValue()).isEqualTo("spec_scrum76_v1");
        assertThat(body.get("targetSpecificationVersionId").stringValue()).isEqualTo("spec_scrum76_v2");
        assertThat(body.get("status").stringValue()).isEqualTo("SUBMITTED");
        assertThat(body.get("createdAt").stringValue()).endsWith("Z");

        assertThat(jdbc.queryForMap("SELECT * FROM change_request WHERE change_request_id = ?", id))
                .containsEntry("change_type", "INGREDIENT_SPEC")
                .containsEntry("status", "SUBMITTED")
                .containsEntry("requested_by_user_id", "user_change_manager")
                .containsEntry("from_specification_version_id", "spec_scrum76_v1")
                .containsEntry("to_specification_version_id", "spec_scrum76_v2")
                .containsEntry("from_formula_version_id", null)
                .containsEntry("to_formula_version_id", null)
                .containsEntry("from_rule_set_version_id", null)
                .containsEntry("to_rule_set_version_id", null)
                .containsEntry("data_provenance_id", "prov_scenario_input");
        assertThat(jdbc.queryForList("SELECT event_type, entity_type, entity_id, actor_user_id, correlation_id, "
                + "data_provenance_id FROM audit_event")).containsExactly(Map.of(
                "event_type", "CHANGE_REQUEST_CREATED", "entity_type", "CHANGE_REQUEST", "entity_id", id,
                "actor_user_id", "user_change_manager", "correlation_id", id,
                "data_provenance_id", "prov_scenario_input"));

        var read = request("GET", PATH + "/" + id, null, AUDITOR);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(body);
        assertThat(Instant.parse(body.get("createdAt").stringValue()))
                .isEqualTo(jdbc.queryForObject("SELECT requested_at FROM change_request", java.time.LocalDateTime.class)
                        .toInstant(java.time.ZoneOffset.UTC));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "[]",
            "{}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"supplierMaterialId\":\"mat_scrum76_base\","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\"}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"supplierMaterialId\":\"mat_scrum76_base\","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\",\"requestedByUserId\":\"user_admin\"}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"supplierMaterialId\":\" \","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\"}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"supplierMaterialId\":7,"
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\"}",
            "{\"changeType\":\"FORMULA\",\"supplierMaterialId\":\"mat_scrum76_base\","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\"}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"changeType\":\"INGREDIENT_SPEC\","
                    + "\"supplierMaterialId\":\"mat_scrum76_base\","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v1\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\"}",
            "{\"changeType\":\"INGREDIENT_SPEC\",\"supplierMaterialId\":\"mat_scrum76_base\","
                    + "\"previousSpecificationVersionId\":\"spec_scrum76_v2\","
                    + "\"targetSpecificationVersionId\":\"spec_scrum76_v2\"}"
    })
    void malformedOrUnsupportedRequestsAreInvalidAndWriteNothing(String body) throws Exception {
        assertError(request("POST", PATH, body, CHANGE_MANAGER), 400, "INVALID_REQUEST");
        assertNothingWritten();
    }

    @Test
    void identityIsRequiredAndCreationNeedsTheChangeRequestPermission() throws Exception {
        assertError(create("spec_scrum76_v1", "spec_scrum76_v2", null), 401, "AUTHENTICATION_REQUIRED");
        assertError(create("spec_scrum76_v1", "spec_scrum76_v2", "unknown-subject"), 401, "AUTHENTICATION_REQUIRED");
        assertError(create("spec_scrum76_v1", "spec_scrum76_v2", LABEL_OFFICER), 403, "AUTHORIZATION_DENIED");
        assertError(request("GET", PATH + "/any", null, null), 401, "AUTHENTICATION_REQUIRED");
        assertNothingWritten();
    }

    @Test
    void unknownReferencesAndChangeRequestsAreNotFound() throws Exception {
        assertError(request("POST", PATH, body("mat_missing", "spec_scrum76_v1", "spec_scrum76_v2"), CHANGE_MANAGER),
                404, "RESOURCE_NOT_FOUND");
        assertError(create("spec_scrum76_v1", "spec_missing", CHANGE_MANAGER), 404, "RESOURCE_NOT_FOUND");
        assertError(create("spec_missing", "spec_scrum76_v2", CHANGE_MANAGER), 404, "RESOURCE_NOT_FOUND");
        assertError(request("GET", PATH + "/cr-missing", null, AUDITOR), 404, "RESOURCE_NOT_FOUND");
        assertNothingWritten();
    }

    @Test
    void domainPreconditionsAreCheckedBeforeAnyDatabaseConstraint() throws Exception {
        assertError(create("spec_chocolate_v1", "spec_scrum76_v2", CHANGE_MANAGER),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        assertError(create("spec_scrum76_v2", "spec_scrum76_v3", CHANGE_MANAGER), 422, "SPECIFICATION_NOT_RELEASED");
        assertError(create("spec_scrum76_v3", "spec_scrum76_v2", CHANGE_MANAGER), 422, "SPECIFICATION_NOT_RELEASED");
        assertError(create("spec_scrum76_v2", "spec_scrum76_v4", CHANGE_MANAGER), 422, "SPECIFICATION_NOT_EFFECTIVE");
        assertNothingWritten();
    }

    @Test
    void theSameOpenChangeCannotBeRequestedTwice() throws Exception {
        assertThat(create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER).statusCode()).isEqualTo(201);

        assertError(create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER), 409, "DATA_CONFLICT");
        assertThat(count("change_request")).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOneChangeRequest() throws Exception {
        int requests = 4;
        var start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER).statusCode();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                codes.add(status.get(60, TimeUnit.SECONDS));
            }
            assertThat(codes).containsOnly(201, 409).containsOnlyOnce(201);
        }
        assertThat(count("change_request")).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(1);
    }

    @Test
    void aFailedAuditRollsBackTheChangeRequestAndTheAuditRow() throws Exception {
        // Stub the spy behind the transactional proxy; the proxy itself requires an active transaction.
        AuditApplicationService target = AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("audit store unavailable");
        }).when(target).recordImpactEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        var response = create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("audit store unavailable");
        assertNothingWritten();
    }

    private HttpResponse<String> create(String previous, String target, String subject) throws Exception {
        return request("POST", PATH, body(MATERIAL, previous, target), subject);
    }

    private static String body(String material, String previous, String target) {
        return JSON.writeValueAsString(Map.of(
                "changeType", "INGREDIENT_SPEC",
                "supplierMaterialId", material,
                "previousSpecificationVersionId", previous,
                "targetSpecificationVersionId", target));
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

    private void assertNothingWritten() {
        assertThat(count("change_request")).isZero();
        assertThat(count("audit_event")).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
