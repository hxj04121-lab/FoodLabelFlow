package com.spectrace.impact;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-85: the first half of the SOY demo on a fresh V3 database with no SQL fixture.
 * The Data Admin creates and releases Spec V2 and batch-adopts it; the Change Manager creates
 * the ChangeRequest and runs impact analysis. The result must still match M2's golden
 * S3-M2-SOY-SPEC-V2-IMPACT v1, which SCRUM-80 previously reached only through a SQL fixture.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S4SpecV2DemoPathMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOLDEN = "/golden/s3-m2-soy-spec-v2-impact-v1.csv";
    private static final String DATA_ADMIN = "dev-external-admin";
    private static final String CHANGE_MANAGER = "dev-external-change-manager";
    private static final String MATERIAL = "mat_chocolate_base";
    private static final String SPEC_V1 = "spec_chocolate_v1";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";

    // Started eagerly: with PER_CLASS the context loads before any extension's beforeAll.
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("scrum85").withPassword("scrum85_password");

    static {
        MYSQL.start();
    }

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

    private Map<String, String> golden;
    private Map<String, String> formulasBeforeAdoption;
    private String specV2;
    private JsonNode adoption;
    private JsonNode adoptionReplay;
    private JsonNode analysis;

    @BeforeAll
    void runTheDemoPathThroughTheApiOnly() throws Exception {
        golden = golden();
        formulasBeforeAdoption = currentFormulas();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM impact_analysis_run", Integer.class)).isZero();

        // Data Admin: Spec V2 = the released V1 components plus Soy Lecithin, released today.
        JsonNode v1 = body(request("GET", "/api/catalog/specifications/" + SPEC_V1, null, DATA_ADMIN), 200);
        ArrayNode components = JSON.createArrayNode();
        for (JsonNode component : v1.get("components")) {
            components.add(component(component.get("ingredient_id").stringValue(),
                    component.get("raw_phrase").stringValue(), component.get("match_rule").stringValue()));
        }
        components.add(component("ing_soy_lecithin", "Soy lecithin", "Supplier Spec V2 declaration"));
        ObjectNode draft = JSON.createObjectNode()
                .put("materialId", MATERIAL)
                .put("effectiveDate", LocalDate.now(ZoneOffset.UTC).toString())
                .put("provenanceId", "prov_project_seed");
        draft.set("components", components);
        specV2 = body(request("POST", "/api/catalog/specifications", draft.toString(), DATA_ADMIN), 201)
                .get("specification_version_id").stringValue();
        body(request("POST", "/api/catalog/specifications/" + specV2 + "/release", null, DATA_ADMIN), 200);

        String adopt = "/api/catalog/specifications/" + specV2 + "/formula-adoptions";
        adoption = body(request("POST", adopt, null, DATA_ADMIN), 200);
        adoptionReplay = body(request("POST", adopt, null, DATA_ADMIN), 200);

        // Change Manager: the ChangeRequest and the impact run.
        String changeRequestId = body(request("POST", "/api/v1/change-requests", """
                {"changeType":"INGREDIENT_SPEC","supplierMaterialId":"%s",
                 "previousSpecificationVersionId":"%s","targetSpecificationVersionId":"%s",
                 "description":"Chocolate Base Spec V2 adds Soy Lecithin"}""".formatted(MATERIAL, SPEC_V1, specV2),
                CHANGE_MANAGER), 201).get("changeRequestId").stringValue();
        analysis = body(request("POST", "/api/v1/change-requests/" + changeRequestId + "/impact-analyses",
                "{\"ruleSetVersionId\":\"" + RULE_SET + "\"}", CHANGE_MANAGER), 201);
    }

    @Test
    void batchAdoptionCreatesOneReleasedNPlusOnePerGoldenRelevantProduct() {
        Set<String> relevant = new TreeSet<>(golden.keySet());
        relevant.removeAll(productsWith("EXCLUDED_NO_FINDING"));

        assertThat(adoption.get("targetSpecificationVersionId").stringValue()).isEqualTo(specV2);
        assertThat(adoption.get("candidateCount").intValue()).isEqualTo(40);
        assertThat(adoption.get("adoptedCount").intValue()).isEqualTo(40);
        assertThat(adoption.get("skippedCount").intValue()).isZero();
        assertThat(adoption.get("failedCount").intValue()).isZero();
        assertThat(results(adoption)).extracting(row -> row.get("productId").stringValue())
                .containsExactlyElementsOf(relevant);
        for (JsonNode row : results(adoption)) {
            String productId = row.get("productId").stringValue();
            Map<String, Object> formula = jdbc.queryForMap("""
                    SELECT fv.formula_version_id, fv.lifecycle_status, fv.is_current_released, fi.specification_version_id
                    FROM product p
                    JOIN formula_version fv ON fv.formula_version_id = p.current_formula_version_id
                    JOIN formula_item fi ON fi.formula_version_id = fv.formula_version_id AND fi.supplier_material_id = ?
                    WHERE p.product_id = ?
                    """, MATERIAL, productId);
            assertThat(row.get("status").stringValue()).isEqualTo("ADOPTED");
            assertThat(row.get("replayed").booleanValue()).isFalse();
            assertThat(formula).containsEntry("formula_version_id", row.get("formulaVersionId").stringValue())
                    .containsEntry("lifecycle_status", "RELEASED").containsEntry("is_current_released", "Y")
                    .containsEntry("specification_version_id", specV2);
        }
        // Released history is untouched: N keeps backing the published label, controls keep their formula.
        Map<String, String> after = currentFormulas();
        for (String control : productsWith("EXCLUDED_NO_FINDING")) {
            assertThat(after.get(control)).isEqualTo(formulasBeforeAdoption.get(control));
        }
        for (String product : relevant) {
            assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM formula_version WHERE formula_version_id = ?",
                    String.class, formulasBeforeAdoption.get(product))).isEqualTo("RELEASED");
        }
    }

    @Test
    void repeatingTheAdoptionReplaysEverySavedFormulaWithoutCreatingMore() {
        assertThat(adoptionReplay.get("adoptedCount").intValue()).isEqualTo(40);
        assertThat(adoptionReplay.get("failedCount").intValue()).isZero();
        Map<String, String> first = formulaByProduct(adoption);
        assertThat(formulaByProduct(adoptionReplay)).isEqualTo(first);
        assertThat(results(adoptionReplay)).allSatisfy(row -> assertThat(row.get("replayed").booleanValue()).isTrue());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_adoption_batch_item WHERE target_specification_version_id = ?",
                Integer.class, specV2)).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_item WHERE specification_version_id = ?",
                Integer.class, specV2)).isEqualTo(40);
    }

    @Test
    void impactAnalysisOnTheLiveAdoptionMatchesTheGoldenWithReadableProductNames() {
        Map<String, String> expected = new TreeMap<>(golden);
        expected.values().removeIf("EXCLUDED_NO_FINDING"::equals);
        Map<String, String> actual = new TreeMap<>(findings().stream().collect(Collectors.toMap(
                finding -> finding.get("productId").stringValue(), finding -> finding.get("outcome").stringValue())));

        assertThat(actual).isEqualTo(expected).hasSize(40);
        assertThat(analysis.get("relevantProductCount").intValue()).isEqualTo(40);
        assertThat(analysis.get("noActionCount").intValue()).isEqualTo(20);
        assertThat(analysis.get("reviewRequiredCount").intValue()).isEqualTo(20);
        assertThat(findings()).extracting(finding -> finding.get("productId").stringValue())
                .doesNotContainAnyElementsOf(productsWith("EXCLUDED_NO_FINDING"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_task", Integer.class)).isEqualTo(20);

        for (JsonNode finding : findings()) {
            String productId = finding.get("productId").stringValue();
            assertThat(finding.get("productDisplayName").stringValue()).isNotBlank().isEqualTo(jdbc.queryForObject(
                    "SELECT product_description FROM product WHERE product_id = ?", String.class, productId));
            assertThat(finding.get("proposedFormulaVersionId").stringValue())
                    .isEqualTo(formulaByProduct(adoption).get(productId));
            assertThat(finding.get("currentFormulaVersionId").stringValue())
                    .isEqualTo(formulasBeforeAdoption.get(productId));
        }
        // The outcome discriminator alone selects the actionable subset that carries review tasks.
        List<JsonNode> reviewRequired = findings().stream()
                .filter(finding -> finding.get("outcome").stringValue().equals("REVIEW_REQUIRED")).toList();
        assertThat(reviewRequired).hasSize(20).allSatisfy(finding -> {
            assertThat(finding.get("missingAllergenCodes").toString()).isEqualTo("[\"SOY\"]");
            assertThat(finding.get("reviewTask").get("reviewTaskId").stringValue()).isNotBlank();
        });
    }

    private static ObjectNode component(String ingredientId, String rawPhrase, String matchRule) {
        return JSON.createObjectNode().put("ingredientId", ingredientId).put("rawPhrase", rawPhrase)
                .put("matchRule", matchRule);
    }

    private Map<String, String> currentFormulas() {
        return jdbc.queryForList("SELECT product_id, current_formula_version_id FROM product").stream()
                .collect(Collectors.toMap(row -> (String) row.get("product_id"),
                        row -> (String) row.get("current_formula_version_id")));
    }

    private static List<JsonNode> results(JsonNode summary) {
        return StreamSupport.stream(summary.get("results").spliterator(), false).toList();
    }

    private static Map<String, String> formulaByProduct(JsonNode summary) {
        return results(summary).stream().collect(Collectors.toMap(row -> row.get("productId").stringValue(),
                row -> row.get("formulaVersionId").stringValue()));
    }

    private List<JsonNode> findings() {
        return StreamSupport.stream(analysis.get("findings").spliterator(), false).toList();
    }

    private Set<String> productsWith(String outcome) {
        return golden.entrySet().stream().filter(entry -> entry.getValue().equals(outcome))
                .map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
    }

    /** product → outcome from M2's versioned golden. */
    private static Map<String, String> golden() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                S4SpecV2DemoPathMySqlTest.class.getResourceAsStream(GOLDEN), GOLDEN), StandardCharsets.UTF_8))) {
            Map<String, String> outcomes = reader.lines().skip(1).map(line -> line.split(",", -1))
                    .collect(Collectors.toMap(columns -> columns[10], columns -> columns[9]));
            assertThat(outcomes).hasSize(60);
            return outcomes;
        }
    }

    private static JsonNode body(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String body, String subject) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", subject)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
