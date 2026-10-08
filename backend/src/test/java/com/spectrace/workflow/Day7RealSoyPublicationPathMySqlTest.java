package com.spectrace.workflow;

import com.spectrace.identity.application.IdentityService;
import com.spectrace.workflow.application.LabelReviewService;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local Day7 acceptance diagnostic. Only released specification input is fixture SQL.
 * Formula adoption, impact/tasks, drafts, validation, decisions and publication must
 * use real production code. No validation status or label content is seeded.
 * HTTP covers adoption, impact, draft and evaluator; review/publication use the actual
 * application service because this checkout exposes no workflow HTTP controller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Day7RealSoyPublicationPathMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("spectrace_test")
            .withPassword("spectrace_test_password");
    static { MYSQL.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @AfterAll
    static void stopOnlyThisTestsDatabase() { MYSQL.stop(); }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityService identities;
    @Autowired private LabelReviewService reviews;

    @Test
    @Timeout(240)
    void soyReviewProductsMustReachRealPassingValidationIndependentApprovalAndPublication() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/fixtures/s3-soy-spec-v2-adoption.sql"))) {
            String fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int boundary = fixture.indexOf("INSERT INTO formula_version");
            assertThat(boundary).isPositive();
            String inputOnly = fixture.substring(0, boundary);
            assertThat(inputOnly).doesNotContain("validation_run", "label_version", "review_task");
            new ResourceDatabasePopulator(new ByteArrayResource(inputOnly.getBytes(StandardCharsets.UTF_8)))
                    .execute(Objects.requireNonNull(jdbc.getDataSource()));
        }
        Map<String, String> golden = golden();
        Map<String, Snapshot> originals = new TreeMap<>();
        for (String product : golden.keySet()) originals.put(product, snapshot(product));
        Map<String, String> adopted = new TreeMap<>();
        Map<String, String> published = new TreeMap<>();
        List<Map<String, Object>> validations = new ArrayList<>();
        List<Map<String, Object>> workflow = new ArrayList<>();
        var maker = identities.authenticate("DEV_EXTERNAL", "dev-external-label-officer");
        var checker = identities.authenticate("DEV_EXTERNAL", "dev-external-qa-approver");
        var publisher = identities.authenticate("DEV_EXTERNAL", "dev-external-publisher");
        assertThat(maker.hasPermission("LABEL.VALIDATE")).isTrue();
        assertThat(checker.hasPermission("LABEL.APPROVE")).isTrue();
        assertThat(publisher.hasPermission("LABEL.PUBLISH")).isTrue();
        assertThat(checker.userId()).isNotEqualTo(maker.userId());
        SoftAssertions acceptance = new SoftAssertions();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("browserExecuted", false);
        evidence.put("baselineCounts", Map.of("approvalRecords", count("approval_record"),
                "publicationRecords", count("publication_record")));
        evidence.put("sqlWrites", List.of("released spec_chocolate_v2 and its specification components only"));

        try (var client = HttpClient.newHttpClient()) {
            for (var entry : golden.entrySet()) {
                if ("EXCLUDED_NO_FINDING".equals(entry.getValue())) continue;
                String product = entry.getKey();
                Snapshot old = originals.get(product);
                var result = request(client, "POST", "/api/catalog/products/" + product + "/formula-adoptions",
                        """
                        {"sourceFormulaVersionId":"%s","targetSpecificationVersionId":"spec_chocolate_v2"}
                        """.formatted(old.formulaId()), "dev-external-admin");
                assertThat(result.statusCode()).as("actual adoption of %s: %s", product, result.body()).isEqualTo(201);
                String newId = JSON.readTree(result.body()).get("formula_version_id").stringValue();
                adopted.put(product, newId);
                assertThat(newId).isNotEqualTo(old.formulaId());
                assertThat(jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", newId))
                        .containsEntry("lifecycle_status", "RELEASED").containsEntry("is_current_released", "Y")
                        .containsEntry("version_number", ((Number) old.formula().get("version_number")).intValue() + 1);
                var expectedItems = old.items().stream().map(item -> {
                    var copy = new LinkedHashMap<>(item);
                    if ("mat_chocolate_base".equals(copy.get("supplier_material_id")))
                        copy.put("specification_version_id", "spec_chocolate_v2");
                    return (Map<String, Object>) copy;
                }).toList();
                assertThat(items(newId)).isEqualTo(expectedItems);
            }
            assertThat(adopted).hasSize(40);
            var created = request(client, "POST", "/api/v1/change-requests", """
                    {"changeType":"INGREDIENT_SPEC","supplierMaterialId":"mat_chocolate_base",
                     "previousSpecificationVersionId":"spec_chocolate_v1",
                     "targetSpecificationVersionId":"spec_chocolate_v2",
                     "description":"Day7 real SOY adoption through evaluator and publication"}
                    """, "dev-external-change-manager");
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            String changeId = JSON.readTree(created.body()).get("changeRequestId").stringValue();
            String trigger = "/api/v1/change-requests/" + changeId + "/impact-analyses";
            String ruleBody = "{\"ruleSetVersionId\":\"" + RULE_SET + "\"}";
            var run = request(client, "POST", trigger, ruleBody, "dev-external-change-manager");
            assertThat(run.statusCode()).as(run.body()).isEqualTo(201);
            JsonNode analysis = JSON.readTree(run.body());
            List<JsonNode> findings = StreamSupport.stream(analysis.get("findings").spliterator(), false).toList();
            Map<String, String> expectedOutcomes = new TreeMap<>(golden);
            expectedOutcomes.values().removeIf("EXCLUDED_NO_FINDING"::equals);
            assertThat(findings.stream().collect(Collectors.toMap(
                    f -> f.get("productId").stringValue(), f -> f.get("outcome").stringValue())))
                    .isEqualTo(expectedOutcomes).hasSize(40);
            assertThat(analysis.get("relevantProductCount").intValue()).isEqualTo(40);
            assertThat(analysis.get("noActionCount").intValue()).isEqualTo(20);
            assertThat(analysis.get("reviewRequiredCount").intValue()).isEqualTo(20);
            for (JsonNode finding : findings) {
                String product = finding.get("productId").stringValue();
                Snapshot old = originals.get(product);
                assertThat(finding.get("currentLabelVersionId").stringValue()).isEqualTo(old.labelId());
                assertThat(finding.get("currentFormulaVersionId").stringValue()).isEqualTo(old.formulaId());
                assertThat(finding.get("proposedFormulaVersionId").stringValue()).isEqualTo(adopted.get(product));
                if ("REVIEW_REQUIRED".equals(golden.get(product))) {
                    assertThat(finding.get("missingAllergenCodes").toString()).isEqualTo("[\"SOY\"]");
                    assertThat(finding.get("reviewTask").get("draftLabelVersionId").isNull()).isTrue();
                } else {
                    assertThat(finding.get("missingAllergenCodes")).isEmpty();
                    assertThat(finding.has("reviewTask")).isFalse();
                }
            }
            var replay = request(client, "POST", trigger, ruleBody, "dev-external-change-manager");
            var get = request(client, "GET", "/api/v1/impact-analyses/"
                    + analysis.get("impactAnalysisId").stringValue(), null, "dev-external-change-manager");
            assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
            assertThat(get.statusCode()).as(get.body()).isEqualTo(200);
            assertThat(JSON.readTree(replay.body())).isEqualTo(analysis);
            assertThat(JSON.readTree(get.body())).isEqualTo(analysis);
            assertThat(count("impact_analysis_run")).isEqualTo(1);
            assertThat(count("impact_finding")).isEqualTo(40);
            assertThat(count("review_task")).isEqualTo(20);
            evidence.put("analysis", analysis);
            evidence.put("adoptions", adopted);
            for (JsonNode finding : findings) {
                String product = finding.get("productId").stringValue();
                if (!"REVIEW_REQUIRED".equals(golden.get(product))) continue;
                String taskId = finding.get("reviewTask").get("reviewTaskId").stringValue();
                var draftResponse = request(client, "POST", "/api/labels/drafts",
                        "{\"productId\":\"" + product + "\",\"jurisdictionCode\":\"US\"}",
                        "dev-external-label-officer");
                assertThat(draftResponse.statusCode()).as(draftResponse.body()).isEqualTo(201);
                JsonNode draft = JSON.readTree(draftResponse.body());
                String draftId = draft.get("labelVersionId").stringValue();
                assertThat(draft.get("formulaVersionId").stringValue()).isEqualTo(adopted.get(product));
                assertThat(task(taskId)).containsEntry("draft_label_version_id", draftId)
                        .containsEntry("status", "OPEN").containsEntry("target_label_version_id", draftId);
                var validation = request(client, "POST", "/api/v1/label-versions/" + draftId + "/validation-runs",
                        ruleBody, "dev-external-label-officer");
                var validationEvidence = new LinkedHashMap<String, Object>();
                validationEvidence.put("productId", product);
                validationEvidence.put("draftId", draftId);
                validationEvidence.put("draft", draft);
                validationEvidence.put("declarations", declarations(draftId));
                validationEvidence.put("httpStatus", validation.statusCode());
                validationEvidence.put("response", JSON.readTree(validation.body()));
                validations.add(validationEvidence);
                System.out.println("DAY7_VALIDATION=" + JSON.writeValueAsString(validationEvidence));
                acceptance.assertThat(validation.statusCode()).as("real evaluator HTTP for %s", product).isEqualTo(201);
                JsonNode validated = JSON.readTree(validation.body());
                acceptance.assertThat(validated.path("status").stringValue())
                        .as("real SOY evaluator must pass for %s without SQL PASSED fixture", product).isEqualTo("PASSED");
                if (validation.statusCode() == 201) {
                    String validationId = validated.get("validationRunId").stringValue();
                    var stored = request(client, "GET", "/api/v1/validation-runs/" + validationId, null,
                            "dev-external-label-officer");
                    assertThat(stored.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(stored.body())).isEqualTo(validated);
                    assertThat(jdbc.queryForObject("SELECT status FROM validation_run WHERE validation_run_id=?",
                            String.class, validationId)).isEqualTo(validated.get("status").stringValue());
                }
                var state = new LinkedHashMap<String, Object>();
                state.put("productId", product);
                state.put("taskId", taskId);
                state.put("draftId", draftId);
                int auditBeforeSubmission = Objects.requireNonNull(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_event WHERE entity_id=?", Integer.class, draftId));
                RuntimeException submissionFailure = null;
                try { reviews.submitForReview(draftId, maker); }
                catch (RuntimeException failure) { submissionFailure = failure; }
                state.put("submissionFailure", submissionFailure == null ? null : submissionFailure.toString());
                state.put("taskAfterSubmission", task(taskId));
                state.put("labelAfterSubmission", jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", draftId));
                workflow.add(state);
                if (submissionFailure != null) {
                    assertThat(task(taskId)).containsEntry("status", "OPEN")
                            .containsEntry("draft_label_version_id", draftId)
                            .containsEntry("target_label_version_id", draftId)
                            .containsEntry("decision", null).containsEntry("resolved_at", null)
                            .containsEntry("resolved_by_user_id", null);
                    assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?",
                            String.class, draftId)).isEqualTo("DRAFT");
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE label_version_id=?",
                            Integer.class, draftId)).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record WHERE label_version_id=?",
                            Integer.class, draftId)).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=?",
                            Integer.class, draftId)).isEqualTo(auditBeforeSubmission);
                    state.put("rejectionAtomicityAsserted", true);
                    acceptance.assertThat(submissionFailure).as("actual submitForReview for %s", product).isNull();
                    continue; // Approval/publication were blocked, never declared successful.
                }
                assertThat(task(taskId)).containsEntry("status", "IN_REVIEW")
                        .containsEntry("target_label_version_id", draftId);
                reviews.recordDecision(draftId, "APPROVE", "Day7 independent actual checker approval", checker);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE label_version_id=? "
                        + "AND review_task_id=? AND decision='APPROVE' AND decided_by_user_id=?",
                        Integer.class, draftId, taskId, checker.userId())).isEqualTo(1);
                reviews.publishReviewTask(taskId, draftId, publisher);
                published.put(product, draftId);
                assertThat(task(taskId)).containsEntry("status", "CLOSED").containsEntry("decision", "APPROVE");
                assertThat(task(taskId).get("resolved_at")).isNotNull();
                assertThat(jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", draftId))
                        .containsEntry("lifecycle_status", "PUBLISHED").containsEntry("is_current_published", "Y");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record WHERE label_version_id=?",
                        Integer.class, draftId)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=? "
                        + "AND event_type='LABEL_PUBLISHED'", Integer.class, draftId)).isEqualTo(1);
            }
            assertThat(validations).hasSize(20);
            assertThat(count("review_task")).isEqualTo(20);
            for (var entry : golden.entrySet()) {
                if (!"REVIEW_REQUIRED".equals(entry.getValue()))
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_task WHERE product_id=?",
                            Integer.class, entry.getKey())).isZero();
            }
        }
        for (var entry : originals.entrySet()) {
            String product = entry.getKey();
            Snapshot old = entry.getValue();
            assertThat(jdbc.queryForMap("SELECT current_formula_version_id,current_published_label_version_id "
                    + "FROM product WHERE product_id=?", product))
                    .containsEntry("current_formula_version_id", adopted.getOrDefault(product, old.formulaId()))
                    .containsEntry("current_published_label_version_id", published.getOrDefault(product, old.labelId()));
            var expectedLabel = new LinkedHashMap<>(old.label());
            if (published.containsKey(product)) {
                expectedLabel.put("lifecycle_status", "SUPERSEDED");
                expectedLabel.put("is_current_published", "N");
                expectedLabel.put("current_published_label_scope_key", null);
            }
            assertThat(jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", old.labelId()))
                    .isEqualTo(expectedLabel);
            assertThat(declarations(old.labelId())).isEqualTo(old.declarations());
            assertThat(items(old.formulaId())).isEqualTo(old.items());
            var expectedFormula = new LinkedHashMap<>(old.formula());
            if (adopted.containsKey(product)) {
                expectedFormula.put("is_current_released", "N");
                expectedFormula.put("current_formula_product_id", null);
            }
            assertThat(jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", old.formulaId()))
                    .isEqualTo(expectedFormula);
        }
        acceptance.assertThat(published).as("all 20 actual SOY publications").hasSize(20);
        evidence.put("validations", validations);
        evidence.put("workflow", workflow);
        evidence.put("published", published);
        evidence.put("counts", Map.of("runs", count("impact_analysis_run"), "findings", count("impact_finding"),
                "tasks", count("review_task"), "validations", count("validation_run"),
                "approvalRecords", count("approval_record"), "publicationRecords", count("publication_record")));
        evidence.put("originalLabelSnapshotsPreserved", true);
        String target = System.getProperty("day7.evidence");
        if (target != null) Files.writeString(Path.of(target), JSON.writeValueAsString(evidence), StandardCharsets.UTF_8);
        System.out.println("DAY7_FLOW_RESULT=" + JSON.writeValueAsString(evidence.get("counts"))
                + "; real publications=" + published.size() + "; original labels preserved=60; browser=false");
        acceptance.assertAll();
    }

    private int count(String table) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    private Map<String, Object> task(String taskId) {
        return jdbc.queryForMap("SELECT review_task_id,product_id,draft_label_version_id,target_label_version_id,"
                + "status,decision,resolved_by_user_id,resolved_at FROM review_task WHERE review_task_id=?", taskId);
    }

    private Snapshot snapshot(String product) {
        var pointers = jdbc.queryForMap("SELECT current_formula_version_id,current_published_label_version_id "
                + "FROM product WHERE product_id=?", product);
        String formula = (String) pointers.get("current_formula_version_id");
        String label = (String) pointers.get("current_published_label_version_id");
        return new Snapshot(formula, label, jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", formula),
                jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", label), items(formula), declarations(label));
    }

    private List<Map<String, Object>> items(String formula) {
        return jdbc.queryForList("SELECT supplier_material_id,specification_version_id,sequence_no,quantity_value,quantity_unit "
                + "FROM formula_item WHERE formula_version_id=? ORDER BY sequence_no", formula);
    }

    private List<Map<String, Object>> declarations(String label) {
        return jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=? "
                + "ORDER BY label_allergen_declaration_id", label);
    }

    private Map<String, String> golden() throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/golden/s3-m2-soy-spec-v2-impact-v1.csv")), StandardCharsets.UTF_8))) {
            Map<String, String> result = reader.lines().skip(1).map(line -> line.split(",", -1))
                    .collect(Collectors.toMap(columns -> columns[10], columns -> columns[9],
                            (first, second) -> { throw new IllegalStateException("Duplicate golden product"); }, TreeMap::new));
            assertThat(result).hasSize(60);
            return result;
        }
    }

    private HttpResponse<String> request(HttpClient client, String method, String endpoint,
                                         String body, String subject) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Snapshot(String formulaId, String labelId, Map<String, Object> formula,
                            Map<String, Object> label, List<Map<String, Object>> items,
                            List<Map<String, Object>> declarations) {}
}
