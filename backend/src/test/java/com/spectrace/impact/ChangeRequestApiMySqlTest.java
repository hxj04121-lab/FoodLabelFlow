package com.spectrace.impact;

import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.catalog.infrastructure.JdbcSpecificationVersionLookupAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/** SCRUM-76: real HTTP, committed MySQL rows and same-transaction audit, with no test-managed transaction. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
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
    private static final String DESCRIPTION = "Chocolate Base Spec V2 adds Soy Lecithin";

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
    @MockitoSpyBean
    private JdbcSpecificationVersionLookupAdapter specifications;

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
                "targetSpecificationVersionId", "description", "status", "createdAt");
        assertThat(body.get("changeType").stringValue()).isEqualTo("INGREDIENT_SPEC");
        assertThat(body.get("supplierMaterialId").stringValue()).isEqualTo(MATERIAL);
        assertThat(body.get("previousSpecificationVersionId").stringValue()).isEqualTo("spec_scrum76_v1");
        assertThat(body.get("targetSpecificationVersionId").stringValue()).isEqualTo("spec_scrum76_v2");
        assertThat(body.get("description").stringValue()).isEqualTo(DESCRIPTION);
        assertThat(body.get("status").stringValue()).isEqualTo("SUBMITTED");
        assertThat(body.get("createdAt").stringValue()).endsWith("Z");

        assertThat(jdbc.queryForMap("SELECT * FROM change_request WHERE change_request_id = ?", id))
                .containsEntry("change_type", "INGREDIENT_SPEC")
                .containsEntry("status", "SUBMITTED")
                .containsEntry("requested_by_user_id", "user_change_manager")
                .containsEntry("description", DESCRIPTION)
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

    static Stream<String> invalidBodies() {
        Map<String, Object> valid = validBody(MATERIAL, "spec_scrum76_v1", "spec_scrum76_v2");
        return Stream.of(
                "not json",
                "[]",
                "{}",
                json(without(valid, "targetSpecificationVersionId")),
                json(without(valid, "description")),
                json(with(valid, "requestedByUserId", "user_admin")),
                json(with(valid, "supplierMaterialId", " ")),
                json(with(valid, "supplierMaterialId", 7)),
                json(with(valid, "description", " ")),
                json(with(valid, "description", "a".repeat(1001))),
                json(with(valid, "changeType", "FORMULA")),
                "{\"changeType\":\"INGREDIENT_SPEC\"," + json(valid).substring(1));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
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
    void unknownBodyReferencesAre422AndOnlyUnknownPathIdsAre404() throws Exception {
        assertError(request("POST", PATH, body("mat_missing", "spec_scrum76_v1", "spec_scrum76_v2"), CHANGE_MANAGER),
                422, "CHANGE_REFERENCE_NOT_FOUND");
        assertError(create("spec_scrum76_v1", "spec_missing", CHANGE_MANAGER), 422, "CHANGE_REFERENCE_NOT_FOUND");
        assertError(create("spec_missing", "spec_scrum76_v2", CHANGE_MANAGER), 422, "CHANGE_REFERENCE_NOT_FOUND");
        assertError(request("GET", PATH + "/cr-missing", null, AUDITOR), 404, "RESOURCE_NOT_FOUND");
        assertNothingWritten();
    }

    @Test
    void domainPreconditionsAreCheckedBeforeAnyDatabaseConstraint() throws Exception {
        assertError(create("spec_scrum76_v2", "spec_scrum76_v2", CHANGE_MANAGER),
                422, "SPECIFICATION_VERSION_UNCHANGED");
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
    void aDescriptionAtTheLengthLimitIsStoredIntact() throws Exception {
        String atLimit = "a".repeat(999) + "\uD83D\uDE00";
        var created = request("POST", PATH,
                json(with(validBody(MATERIAL, "spec_scrum76_v1", "spec_scrum76_v2"), "description", atLimit)),
                CHANGE_MANAGER);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(JSON.readTree(created.body()).get("description").stringValue()).isEqualTo(atLimit);
        assertThat(jdbc.queryForObject("SELECT description FROM change_request", String.class)).isEqualTo(atLimit);
    }

    @Test
    void requestResponseAndErrorCodesMatchTheMergedS3Contract() throws Exception {
        Path root = repositoryRoot();
        Map<String, Object> contract = new Yaml().load(
                Files.readString(root.resolve("docs/contracts/s3-impact-review-publication-api-v1.yaml")));
        Map<String, Object> schemas = map(map(contract.get("components")).get("schemas"));

        assertThat(validBody(MATERIAL, "spec_scrum76_v1", "spec_scrum76_v2").keySet())
                .containsExactlyInAnyOrderElementsOf(list(map(schemas.get("ChangeRequestCreate")).get("required")));
        var created = create("spec_scrum76_v1", "spec_scrum76_v2", CHANGE_MANAGER);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(JSON.readTree(created.body()).properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyElementsOf(list(map(schemas.get("ChangeRequest")).get("required")));

        String matrix = Files.readString(root.resolve("docs/contracts/s3-impact-api-error-matrix-v1.md"));
        for (String code : List.of("INVALID_REQUEST", "AUTHENTICATION_REQUIRED", "AUTHORIZATION_DENIED",
                "RESOURCE_NOT_FOUND", "DATA_CONFLICT", "CHANGE_REFERENCE_NOT_FOUND",
                "SPECIFICATION_MATERIAL_MISMATCH", "SPECIFICATION_NOT_RELEASED", "SPECIFICATION_NOT_EFFECTIVE",
                "SPECIFICATION_VERSION_UNCHANGED", "INTERNAL_ERROR")) {
            assertThat(matrix).as("error matrix lists %s", code).contains("`" + code + "`");
        }
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

    @Test
    void listReturnsRecordedIngredientSpecChangesInIdOrderWithTheSingleItemShape() throws Exception {
        insertListFixture();

        var listed = request("GET", PATH, null, AUDITOR);

        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        JsonNode page = JSON.readTree(listed.body());
        assertThat(ids(page)).containsExactly("cr-list-a", "cr-list-c", "cr-list-e");
        for (JsonNode item : page) {
            var single = request("GET", PATH + "/" + item.get("changeRequestId").stringValue(), null, AUDITOR);
            assertThat(JSON.readTree(single.body())).isEqualTo(item);
        }
        assertThat(page.findValuesAsString("supplierMaterialId"))
                .containsExactly(MATERIAL, MATERIAL, "mat_chocolate_base");
        assertThat(page.findValuesAsString("status")).containsExactly("SUBMITTED", "COMPLETED", "ANALYZED");
    }

    @Test
    void listFiltersBeforePagingAndEndsWithAnEmptyPage() throws Exception {
        insertListFixture();

        assertThat(listIds("?limit=2")).containsExactly("cr-list-a", "cr-list-c");
        assertThat(listIds("?limit=2&offset=2")).containsExactly("cr-list-e");
        assertThat(listIds("?limit=3&offset=0")).containsExactly("cr-list-a", "cr-list-c", "cr-list-e");
        assertThat(listIds("?limit=3&offset=3")).isEmpty();
        assertThat(listIds("?offset=5000000000")).isEmpty();
        assertThat(listIds("?limit=100")).hasSize(3);
    }

    @Test
    void anEmptyCollectionIsASuccessfulEmptyArray() throws Exception {
        var listed = request("GET", PATH, null, AUDITOR);

        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        assertThat(listed.body()).isEqualTo("[]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=101", "limit=-1", "limit=abc", "limit=1.5", "limit=",
            "offset=-1", "offset=x", "limit=1&limit=2", "status=SUBMITTED", "limit=99999999999999999999"})
    void invalidPagingIsRejected(String query) throws Exception {
        assertError(request("GET", PATH + "?" + query, null, AUDITOR), 400, "INVALID_REQUEST");
    }

    @Test
    void listingNeedsOnlyAnActiveIdentityAndWritesNothing() throws Exception {
        insertListFixture();
        int changeRequests = count("change_request");

        assertError(request("GET", PATH, null, null), 401, "AUTHENTICATION_REQUIRED");
        assertError(request("GET", PATH, null, "unknown-subject"), 401, "AUTHENTICATION_REQUIRED");
        var withoutCreatePermission = request("GET", PATH, null, LABEL_OFFICER);
        assertThat(withoutCreatePermission.statusCode()).as(withoutCreatePermission.body()).isEqualTo(200);
        assertThat(count("change_request")).isEqualTo(changeRequests);
        assertThat(count("audit_event")).isZero();
    }

    @Test
    void aFailedSpecificationLookupIsAnErrorNotAnEmptyPage() throws Exception {
        insertListFixture();
        JdbcSpecificationVersionLookupAdapter target = AopTestUtils.getUltimateTargetObject(specifications);
        doThrow(new IllegalStateException("catalog read failed")).when(target).findAllById(anyCollection());

        var listed = request("GET", PATH, null, AUDITOR);

        assertError(listed, 500, "INTERNAL_ERROR");
        assertThat(listed.body()).doesNotContain("catalog read failed");
    }

    /** Seven rows: three listed, plus DRAFT, CANCELLED, FORMULA and RULE_SET rows that sort first or between. */
    private void insertListFixture() {
        insertChange("cr-list-c", "INGREDIENT_SPEC", "COMPLETED", "spec_scrum76_v1", "spec_scrum76_v2");
        insertChange("cr-list-a", "INGREDIENT_SPEC", "SUBMITTED", "spec_scrum76_v1", "spec_scrum76_v2");
        insertChange("cr-list-b", "INGREDIENT_SPEC", "DRAFT", "spec_scrum76_v1", "spec_scrum76_v2");
        insertChange("cr-list-d", "INGREDIENT_SPEC", "CANCELLED", "spec_scrum76_v1", "spec_scrum76_v2");
        insertChange("cr-list-e", "INGREDIENT_SPEC", "ANALYZED", "spec_scrum76_v2", "spec_chocolate_v1");
        insertChange("cr-list-0f", "FORMULA", "SUBMITTED", "formula_1106285_v1", "formula_1106963_v1");
        insertChange("cr-list-0r", "RULE_SET", "SUBMITTED", "ruleset_us_falcpa_demo_v1", "ruleset_us_falcpa_demo_v2");
    }

    private void insertChange(String id, String type, String status, String from, String to) {
        String prefix = switch (type) {
            case "INGREDIENT_SPEC" -> "specification";
            case "FORMULA" -> "formula";
            default -> "rule_set";
        };
        jdbc.update("INSERT INTO change_request(change_request_id, change_request_code, change_type, status, "
                        + "requested_at, requested_by_user_id, description, from_" + prefix + "_version_id, to_"
                        + prefix + "_version_id, data_provenance_id) "
                        + "VALUES (?, ?, ?, ?, '2026-09-30 02:00:00', 'user_change_manager', ?, ?, ?, "
                        + "'prov_scenario_input')",
                id, "CR-" + id, type, status, "List fixture " + id, from, to);
    }

    private List<String> listIds(String query) throws Exception {
        var listed = request("GET", PATH + query, null, AUDITOR);
        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        return ids(JSON.readTree(listed.body()));
    }

    private static List<String> ids(JsonNode page) {
        assertThat(page.isArray()).isTrue();
        return page.findValuesAsString("changeRequestId");
    }

    private HttpResponse<String> create(String previous, String target, String subject) throws Exception {
        return request("POST", PATH, body(MATERIAL, previous, target), subject);
    }

    private static String body(String material, String previous, String target) {
        return json(validBody(material, previous, target));
    }

    private static Map<String, Object> validBody(String material, String previous, String target) {
        var body = new LinkedHashMap<String, Object>();
        body.put("changeType", "INGREDIENT_SPEC");
        body.put("supplierMaterialId", material);
        body.put("previousSpecificationVersionId", previous);
        body.put("targetSpecificationVersionId", target);
        body.put("description", DESCRIPTION);
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        var changed = new LinkedHashMap<>(body);
        changed.put(field, value);
        return changed;
    }

    private static Map<String, Object> without(Map<String, Object> body, String field) {
        var changed = new LinkedHashMap<>(body);
        changed.remove(field);
        return changed;
    }

    private static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static List<String> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream().map(String::valueOf).toList();
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("docs/contracts"))) {
            root = root.getParent();
        }
        assertThat(root).as("repository root containing docs/contracts").isNotNull();
        return root;
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
