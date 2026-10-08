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
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * SCRUM-80: the SOY scenario end to end over HTTP on the V3 seed — change request → run →
 * findings → ReviewTasks — asserted product by product against M2's golden
 * S3-M2-SOY-SPEC-V2-IMPACT v1. Spec V2 and its N+1 adoption come from the shared fixture
 * until M2's real adoption is on main; the class owns its container because they commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "/fixtures/s3-soy-spec-v2-adoption.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SoyGoldenImpactRunMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOLDEN = "/golden/s3-m2-soy-spec-v2-impact-v1.csv";
    private static final String CHANGE_MANAGER = "dev-external-change-manager";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";

    // Started eagerly: with PER_CLASS the context loads before any extension's beforeAll.
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("scrum80").withPassword("scrum80_password");

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
    private String changeRequestId;
    private JsonNode analysis;

    /** The scenario as a user drives it: create the change request, then trigger the run. */
    @BeforeAll
    void runTheSoyScenario() throws Exception {
        golden = golden();
        var created = request("POST", "/api/v1/change-requests", """
                {"changeType":"INGREDIENT_SPEC","supplierMaterialId":"mat_chocolate_base",
                 "previousSpecificationVersionId":"spec_chocolate_v1",
                 "targetSpecificationVersionId":"spec_chocolate_v2",
                 "description":"Chocolate Base Spec V2 adds Soy Lecithin"}""");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        changeRequestId = JSON.readTree(created.body()).get("changeRequestId").stringValue();

        var run = request("POST", "/api/v1/change-requests/" + changeRequestId + "/impact-analyses",
                "{\"ruleSetVersionId\":\"" + RULE_SET + "\"}");
        assertThat(run.statusCode()).as(run.body()).isEqualTo(201);
        analysis = JSON.readTree(run.body());
    }

    @Test
    void everyRelevantProductHasExactlyItsGoldenOutcome() {
        Map<String, String> expected = new TreeMap<>(golden);
        expected.values().removeIf("EXCLUDED_NO_FINDING"::equals);
        Map<String, String> actual = new TreeMap<>(findings().stream().collect(Collectors.toMap(
                finding -> finding.get("productId").stringValue(),
                finding -> finding.get("outcome").stringValue())));

        assertThat(actual).isEqualTo(expected).hasSize(40);
        assertThat(analysis.get("relevantProductCount").intValue()).isEqualTo(40);
        assertThat(analysis.get("noActionCount").intValue()).isEqualTo(20);
        assertThat(analysis.get("reviewRequiredCount").intValue()).isEqualTo(20);
    }

    @Test
    void negativeControlsProduceNoFindingAndNoReviewTask() {
        Set<String> controls = productsWith("EXCLUDED_NO_FINDING");
        assertThat(controls).hasSize(20);

        assertThat(findings()).extracting(finding -> finding.get("productId").stringValue())
                .doesNotContainAnyElementsOf(controls);
        assertThat(jdbc.queryForList("SELECT product_id FROM impact_finding", String.class))
                .doesNotContainAnyElementsOf(controls);
        assertThat(jdbc.queryForList("SELECT product_id FROM review_task", String.class))
                .doesNotContainAnyElementsOf(controls);
    }

    @Test
    void reviewRequiredProductsMissSoyAndEachOpensOneTaskWithoutADraftYet() {
        Set<String> reviewRequired = productsWith("REVIEW_REQUIRED");

        for (JsonNode finding : findings()) {
            String product = finding.get("productId").stringValue();
            if (reviewRequired.contains(product)) {
                assertThat(finding.get("missingAllergenCodes").toString()).isEqualTo("[\"SOY\"]");
                assertThat(finding.get("reviewTask").get("draftLabelVersionId").isNull()).isTrue();
            } else {
                assertThat(finding.get("missingAllergenCodes")).isEmpty();
                assertThat(finding.has("reviewTask")).isFalse();
            }
        }
        assertThat(new TreeSet<>(jdbc.queryForList("""
                SELECT rt.product_id
                FROM review_task rt
                JOIN impact_finding f ON f.impact_finding_id = rt.impact_finding_id
                WHERE f.classification = 'REVIEW_REQUIRED' AND rt.status = 'OPEN'
                  AND rt.draft_label_version_id IS NULL AND rt.assigned_to_user_id = 'user_label_officer'
                """, String.class))).isEqualTo(reviewRequired);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_task", Integer.class)).isEqualTo(20);
    }

    @Test
    void findingsPointAtTheFormulaBehindThePublishedLabelAndTheAdoptedNPlusOne() {
        for (JsonNode finding : findings()) {
            Map<String, Object> product = jdbc.queryForMap("""
                    SELECT p.current_formula_version_id, p.current_published_label_version_id,
                           lv.formula_version_id AS label_formula_version_id
                    FROM product p
                    JOIN label_version lv ON lv.label_version_id = p.current_published_label_version_id
                    WHERE p.product_id = ?
                    """, finding.get("productId").stringValue());
            assertThat(finding.get("currentLabelVersionId").stringValue())
                    .isEqualTo(product.get("current_published_label_version_id"));
            assertThat(finding.get("currentFormulaVersionId").stringValue())
                    .isEqualTo(product.get("label_formula_version_id"));
            assertThat(finding.get("proposedFormulaVersionId").stringValue())
                    .isEqualTo(product.get("current_formula_version_id"))
                    .isNotEqualTo(finding.get("currentFormulaVersionId").stringValue());
        }
    }

    @Test
    void theRunIsPersistedOnceAndTheChangeRequestIsAnalyzed() throws Exception {
        String runId = analysis.get("impactAnalysisId").stringValue();
        assertThat(jdbc.queryForList("SELECT impact_analysis_run_id FROM impact_analysis_run", String.class))
                .containsExactly(runId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM impact_finding WHERE impact_analysis_run_id = ?",
                Integer.class, runId)).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT status FROM change_request WHERE change_request_id = ?",
                String.class, changeRequestId)).isEqualTo("ANALYZED");

        var read = request("GET", "/api/v1/impact-analyses/" + runId, null);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(analysis);
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
                SoyGoldenImpactRunMySqlTest.class.getResourceAsStream(GOLDEN), GOLDEN), StandardCharsets.UTF_8))) {
            Map<String, String> outcomes = reader.lines().skip(1).map(line -> line.split(",", -1))
                    .collect(Collectors.toMap(columns -> columns[10], columns -> columns[9]));
            assertThat(outcomes).hasSize(60);
            return outcomes;
        }
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", CHANGE_MANAGER)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
