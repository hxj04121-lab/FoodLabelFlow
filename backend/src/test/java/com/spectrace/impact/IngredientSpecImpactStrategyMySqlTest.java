package com.spectrace.impact;

import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.RelevantProductDiscovery;
import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.application.strategy.ImpactStrategy;
import com.spectrace.impact.application.strategy.ImpactStrategyRegistry;
import com.spectrace.impact.application.strategy.ProductImpactAssessment;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-56: the Flyway seed and M2 golden through committed, real HTTP adoption,
 * followed by M1 discovery/classification using the real catalog, allergen and label adapters.
 * Released Spec V2 is prerequisite fixture data, not acceptance of the specification-release API.
 * There is deliberately no test-managed transaction across HTTP requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class IngredientSpecImpactStrategyMySqlTest extends MySqlIntegrationTestSupport {
    private static final String GOLDEN = "/golden/s3-m2-soy-spec-v2-impact-v1.csv";
    private static final String CHOCOLATE = "mat_chocolate_base";
    private static final String SPEC_V1 = "spec_chocolate_v1";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String TARGET_PROVENANCE = "prov_scenario_input";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort private int port;
    @Autowired private RelevantProductDiscovery discovery;
    @Autowired private ImpactStrategyRegistry strategies;
    @Autowired private LabelSnapshotPort labels;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Map<String, SeedState> original = new LinkedHashMap<>();
    private final Set<String> touchedProducts = new LinkedHashSet<>();
    private final Set<String> createdFormulaIds = new LinkedHashSet<>();
    private String targetSpecId;
    private String fixtureToken;
    private HttpClient client;

    @BeforeEach
    void snapshotTheGoldenSeedBeforeAnyCommittedWrites() throws IOException {
        fixtureToken = UUID.randomUUID().toString();
        targetSpecId = "spec_chocolate_v2_scrum56_" + fixtureToken;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Set<String> products = golden().values().stream().flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));
        for (String productId : products) original.put(productId, seedState(productId));
    }

    @AfterEach
    void restoreSeedPointersAndRemoveOnlyThisTestsCommittedAdoptions() {
        try {
            if (targetSpecId == null) return;
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                // Trust database ownership, not response IDs, when recovering committed fixtures.
                createdFormulaIds.clear();
                for (String productId : touchedProducts) {
                    Set<String> seedFormulaIds = original.get(productId).formulas().stream()
                            .map(row -> (String) row.get("formula_version_id")).collect(Collectors.toSet());
                    var ownedIds = jdbc.queryForList("""
                            SELECT DISTINCT fv.formula_version_id
                            FROM formula_version fv JOIN formula_item fi
                              ON fi.formula_version_id = fv.formula_version_id
                            WHERE fv.product_id = ? AND fi.specification_version_id = ?
                            """, String.class, productId, targetSpecId);
                    ownedIds.stream().filter(id -> !seedFormulaIds.contains(id)).forEach(createdFormulaIds::add);
                }
                // The generated current-formula key is unique: clear N+1 before restoring old N to Y.
                for (String id : createdFormulaIds)
                    jdbc.update("UPDATE formula_version SET is_current_released='N' WHERE formula_version_id=?", id);
                for (String productId : touchedProducts) {
                    SeedState state = original.get(productId);
                    jdbc.update("""
                            UPDATE product SET current_formula_version_id=?,current_published_label_version_id=?
                            WHERE product_id=?
                            """, state.product().get("current_formula_version_id"),
                            state.product().get("current_published_label_version_id"), productId);
                    for (var formula : state.formulas())
                        jdbc.update("UPDATE formula_version SET is_current_released=? WHERE formula_version_id=?",
                                formula.get("is_current_released"), formula.get("formula_version_id"));
                }
                for (String id : createdFormulaIds) {
                    jdbc.update("DELETE FROM audit_event WHERE entity_type='CATALOG' AND entity_id=?", id);
                    jdbc.update("DELETE FROM formula_item WHERE formula_version_id=?", id);
                    jdbc.update("DELETE FROM formula_version WHERE formula_version_id=?", id);
                }
                jdbc.update("DELETE FROM spec_component WHERE specification_version_id=?", targetSpecId);
                jdbc.update("DELETE FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecId);
            });
            for (var entry : original.entrySet())
                assertThat(seedState(entry.getKey())).as("seed restored: %s", entry.getKey()).isEqualTo(entry.getValue());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ingredient_specification_version WHERE specification_version_id=?",
                    Integer.class, targetSpecId)).isZero();
            for (String id : createdFormulaIds)
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=?", Integer.class, id)).isZero();
        } finally {
            if (client != null) client.close();
        }
    }

    @Test
    void theRegistrySelectsTheIngredientSpecStrategyAndNothingElse() {
        assertThat(strategies.require(ChangeType.INGREDIENT_SPEC).changeType()).isEqualTo(ChangeType.INGREDIENT_SPEC);
        assertThatThrownBy(() -> strategies.require(ChangeType.FORMULA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> strategies.require(ChangeType.RULE_SET)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soyLecithinInChocolateBaseClassifiesEveryRelevantProductAsTheGoldenExpects() throws Exception {
        Map<String, Set<String>> golden = golden();
        createCommittedReleasedSpecV2WithSoyLecithin();
        Map<String, Integer> impactBefore = impactTableCounts();
        List<RelevantProductTarget> before = discovery.discover(CHOCOLATE);
        Set<String> expectedRelevant = new TreeSet<>(golden.get("NO_ACTION"));
        expectedRelevant.addAll(golden.get("REVIEW_REQUIRED"));
        assertThat(before).hasSize(40);
        assertThat(before.stream().map(RelevantProductTarget::productId).collect(Collectors.toSet()))
                .isEqualTo(expectedRelevant);

        Map<String, String> adoptedIds = new LinkedHashMap<>();
        for (RelevantProductTarget product : before)
            adoptedIds.put(product.productId(), adoptSpecV2(product.productId(), product.currentFormulaVersionId()));

        ImpactStrategy strategy = strategies.require(ChangeType.INGREDIENT_SPEC);
        // Adoption copies items with fresh IDs, so the classifier must consume a fresh lookup.
        List<RelevantProductTarget> products = discovery.discover(CHOCOLATE);
        assertThat(products).hasSize(40);
        assertThat(products.stream().map(RelevantProductTarget::productId).collect(Collectors.toSet()))
                .isEqualTo(expectedRelevant);
        Map<String, ProductImpactAssessment> byProduct = products.stream()
                .map(product -> strategy.assess(change(), product, RULE_SET))
                .collect(Collectors.toMap(ProductImpactAssessment::productId, assessment -> assessment));

        assertThat(byProduct.keySet()).doesNotContainAnyElementsOf(golden.get("EXCLUDED_NO_FINDING"));
        assertThat(productsWith(byProduct, ImpactClassification.NO_ACTION)).isEqualTo(golden.get("NO_ACTION"));
        assertThat(productsWith(byProduct, ImpactClassification.REVIEW_REQUIRED)).isEqualTo(golden.get("REVIEW_REQUIRED"));
        for (RelevantProductTarget product : products) {
            SeedState state = original.get(product.productId());
            String source = (String) state.product().get("current_formula_version_id");
            String publishedLabel = (String) state.product().get("current_published_label_version_id");
            String adopted = adoptedIds.get(product.productId());
            ProductImpactAssessment assessment = byProduct.get(product.productId());
            assertThat(product.currentFormulaVersionId()).isEqualTo(adopted).isNotEqualTo(source);
            assertThat(product.currentLabelVersionId()).isEqualTo(publishedLabel);
            List<Map<String, Object>> matching = formulaItems(adopted).stream()
                    .filter(item -> CHOCOLATE.equals(item.get("supplier_material_id"))).toList();
            assertThat(product.matchingFormulaItemIds()).containsExactlyElementsOf(
                    matching.stream().map(item -> (String) item.get("formula_item_id")).toList());
            assertThat(matching).allSatisfy(item -> assertThat(item)
                    .containsEntry("formula_version_id", adopted).containsEntry("specification_version_id", targetSpecId));
            assertThat(assessment.currentFormulaVersionId()).isEqualTo(source);
            assertThat(assessment.proposedFormulaVersionId()).isEqualTo(adopted);
            assertThat(assessment.currentLabelVersionId()).isEqualTo(publishedLabel);
            assertThat(labels.findById(publishedLabel).orElseThrow().isCurrent()).isFalse();
            assertThat(assessment.missingAllergenCodes()).isEqualTo(
                    assessment.classification() == ImpactClassification.REVIEW_REQUIRED ? List.of("SOY") : List.of());
            assertThat(assessment.explanation()).hasSizeLessThanOrEqualTo(1000).contains(targetSpecId);
            assertHistoricalContentAndExactCopy(product.productId(), source, adopted, false);
        }
        assertThat(adoptedIds).hasSize(40);
        assertThat(createdFormulaIds).hasSize(40);
        for (String productId : golden.get("EXCLUDED_NO_FINDING"))
            assertThat(seedState(productId)).as("negative control untouched: %s", productId).isEqualTo(original.get(productId));
        assertThat(impactTableCounts()).isEqualTo(impactBefore);
    }

    @Test
    void beforeAdoptionEveryRelevantProductIsAdoptionPendingAndNothingIsWritten() {
        createCommittedReleasedSpecV2WithSoyLecithin();
        ImpactStrategy strategy = strategies.require(ChangeType.INGREDIENT_SPEC);
        Map<String, Integer> impactBefore = impactTableCounts();

        for (RelevantProductTarget product : discovery.discover(CHOCOLATE)) {
            assertThatThrownBy(() -> strategy.assess(change(), product, RULE_SET))
                    .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(422);
                        assertThat(failure.code()).isEqualTo("FORMULA_ADOPTION_PENDING");
                    });
        }
        assertThat(impactTableCounts()).isEqualTo(impactBefore);
        for (var entry : original.entrySet()) assertThat(seedState(entry.getKey())).isEqualTo(entry.getValue());
    }

    @Test
    void adoptionDoesNotRequireAPublishedLabelButDiscoveryRejectsItBeforeImpactWrites() throws Exception {
        createCommittedReleasedSpecV2WithSoyLecithin();
        RelevantProductTarget product = discovery.discover(CHOCOLATE).getFirst();
        touchedProducts.add(product.productId());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
                jdbc.update("UPDATE product SET current_published_label_version_id=NULL WHERE product_id=?", product.productId()));
        Map<String, Integer> impactBefore = impactTableCounts();

        String adopted = adoptSpecV2(product.productId(), product.currentFormulaVersionId());

        assertThat(jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?",
                String.class, product.productId())).isEqualTo(adopted);
        assertThat(jdbc.queryForObject("SELECT current_published_label_version_id FROM product WHERE product_id=?",
                String.class, product.productId())).isNull();
        assertThatThrownBy(() -> discovery.discover(CHOCOLATE))
                .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("PUBLISHED_LABEL_MISSING");
                    assertThat(failure.getMessage()).contains(product.productId());
                });
        assertThat(impactTableCounts()).isEqualTo(impactBefore);
        assertHistoricalContentAndExactCopy(product.productId(), product.currentFormulaVersionId(), adopted, true);
    }

    /** SQL creates only a prerequisite released/effective spec; N+1 always comes from the HTTP API. */
    private void createCommittedReleasedSpecV2WithSoyLecithin() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("""
                    INSERT INTO ingredient_specification_version(specification_version_id, supplier_material_id,
                        version_number, lifecycle_status, effective_date, released_at, created_by_user_id, data_provenance_id)
                    VALUES (?, ?, 2, 'RELEASED', '2026-09-01', '2026-09-01 09:00:00', 'user_admin', ?)
                    """, targetSpecId, CHOCOLATE, TARGET_PROVENANCE);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id, specification_version_id, ingredient_id, raw_phrase,
                                               match_rule, match_status, sequence_no)
                    SELECT CONCAT(spec_component_id, '_', ?), ?, ingredient_id, raw_phrase, match_rule,
                           match_status, sequence_no
                    FROM spec_component WHERE specification_version_id = ?
                    """, fixtureToken, targetSpecId, SPEC_V1);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id, specification_version_id, ingredient_id, raw_phrase,
                                               match_rule, match_status, sequence_no)
                    VALUES (?, ?, 'ing_soy_lecithin', 'Soy lecithin', 'Project fixture component', 'MATCHED', 3)
                    """, "sc_scrum56_soy_" + fixtureToken, targetSpecId);
        });
    }

    private String adoptSpecV2(String productId, String formulaN) throws Exception {
        touchedProducts.add(productId);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/catalog/products/" + productId + "/formula-adoptions"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", "dev-external-admin")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                        "sourceFormulaVersionId", formulaN, "targetSpecificationVersionId", targetSpecId))))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("adoption of %s: %s", productId, response.body()).isEqualTo(201);
        var body = JSON.readTree(response.body());
        String adopted = body.get("formula_version_id").stringValue();
        createdFormulaIds.add(adopted);
        assertThat(adopted).isNotBlank().isNotEqualTo(formulaN);
        assertThat(body.get("product_id").stringValue()).isEqualTo(productId);
        assertThat(body.get("lifecycle_status").stringValue()).isEqualTo("RELEASED");
        int allocatedVersion = original.get(productId).formulas().stream()
                .mapToInt(formula -> ((Number) formula.get("version_number")).intValue()).max().orElseThrow() + 1;
        assertThat(formula(adopted)).containsEntry("version_number", allocatedVersion)
                .containsEntry("lifecycle_status", "RELEASED").containsEntry("is_current_released", "Y")
                .containsEntry("current_formula_product_id", productId)
                .containsEntry("created_by_user_id", "user_admin").containsEntry("released_by_user_id", "user_admin")
                .containsEntry("data_provenance_id", TARGET_PROVENANCE);
        assertThat(formula(adopted).get("released_at")).isNotNull();
        assertAdoptionAudit(formulaN, adopted);
        return adopted;
    }

    private void assertAdoptionAudit(String source, String adopted) {
        var audits = jdbc.queryForList("""
                SELECT event_type,entity_type,actor_user_id,data_provenance_id,before_value,after_value,correlation_id,
                       JSON_LENGTH(event_payload) AS payloadSize
                FROM audit_event WHERE entity_id=?
                """, adopted);
        assertThat(audits).hasSize(3).extracting(row -> row.get("event_type"))
                .containsExactlyInAnyOrder("FORMULA_CREATED", "FORMULA_RELEASED", "FORMULA_SPECIFICATION_ADOPTED");
        assertThat(audits).allSatisfy(row -> {
            assertThat(row).containsEntry("entity_type", "CATALOG").containsEntry("actor_user_id", "user_admin")
                    .containsEntry("data_provenance_id", TARGET_PROVENANCE).containsEntry("before_value", null)
                    .containsEntry("after_value", null).containsEntry("correlation_id", null);
            assertThat(((Number) row.get("payloadSize")).intValue()).isEqualTo(
                    "FORMULA_SPECIFICATION_ADOPTED".equals(row.get("event_type")) ? 3 : 0);
        });
        var evidence = jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.sourceFormulaVersionId')) AS sourceId,
                       JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.targetSpecificationVersionId')) AS targetId,
                       JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.newFormulaVersionId')) AS newId
                FROM audit_event WHERE entity_id=? AND event_type='FORMULA_SPECIFICATION_ADOPTED'
                """, adopted);
        assertThat(evidence).containsEntry("sourceId", source).containsEntry("targetId", targetSpecId).containsEntry("newId", adopted);
    }

    private void assertHistoricalContentAndExactCopy(String productId, String source, String adopted, boolean removedLabelPointer) {
        SeedState state = original.get(productId);
        for (var old : state.formulas()) {
            var after = formula((String) old.get("formula_version_id"));
            assertThat(historicalFormulaContent(after)).isEqualTo(historicalFormulaContent(old));
            assertThat(after.get("is_current_released")).isEqualTo(
                    source.equals(old.get("formula_version_id")) ? "N" : old.get("is_current_released"));
        }
        for (var old : state.items())
            assertThat(jdbc.queryForMap("SELECT * FROM formula_item WHERE formula_item_id=?", old.get("formula_item_id")))
                    .isEqualTo(old);
        SeedState current = seedState(productId);
        assertThat(current.labels()).isEqualTo(state.labels());
        assertThat(current.declarations()).isEqualTo(state.declarations());
        var expectedProduct = new LinkedHashMap<>(state.product());
        expectedProduct.put("current_formula_version_id", adopted);
        if (removedLabelPointer) expectedProduct.put("current_published_label_version_id", null);
        assertThat(current.product()).isEqualTo(expectedProduct);
        List<Map<String, Object>> sourceItems = state.items().stream()
                .filter(item -> source.equals(item.get("formula_version_id"))).toList();
        List<Map<String, Object>> expectedItems = new ArrayList<>();
        for (var item : sourceItems) {
            var expected = itemContent(item);
            if (CHOCOLATE.equals(item.get("supplier_material_id"))) expected.put("specification_version_id", targetSpecId);
            expectedItems.add(expected);
        }
        var copied = formulaItems(adopted);
        assertThat(copied.stream().map(IngredientSpecImpactStrategyMySqlTest::itemContent).toList()).isEqualTo(expectedItems);
        assertThat(copied.stream().map(item -> item.get("formula_item_id")).toList())
                .doesNotContainAnyElementsOf(state.items().stream().map(item -> item.get("formula_item_id")).toList());
        assertThat(current.formulas()).hasSize(state.formulas().size() + 1);
        assertThat(current.formulas().stream().filter(row -> "Y".equals(row.get("is_current_released"))).toList())
                .hasSize(1).allSatisfy(row -> assertThat(row).containsEntry("formula_version_id", adopted));
    }

    private SeedState seedState(String productId) {
        return new SeedState(jdbc.queryForMap("SELECT * FROM product WHERE product_id=?", productId),
                jdbc.queryForList("SELECT * FROM formula_version WHERE product_id=? ORDER BY version_number,formula_version_id", productId),
                jdbc.queryForList("""
                        SELECT fi.* FROM formula_item fi JOIN formula_version fv ON fv.formula_version_id=fi.formula_version_id
                        WHERE fv.product_id=? ORDER BY fv.version_number,fi.sequence_no,fi.formula_item_id
                        """, productId),
                jdbc.queryForList("SELECT * FROM label_version WHERE product_id=? ORDER BY label_version_id", productId),
                jdbc.queryForList("""
                        SELECT d.* FROM label_allergen_declaration d JOIN label_version lv ON lv.label_version_id=d.label_version_id
                        WHERE lv.product_id=? ORDER BY d.label_allergen_declaration_id
                        """, productId));
    }

    private Map<String, Object> formula(String id) {
        return jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", id);
    }

    private List<Map<String, Object>> formulaItems(String id) {
        return jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY sequence_no,formula_item_id", id);
    }

    private Map<String, Integer> impactTableCounts() {
        var counts = new LinkedHashMap<String, Integer>();
        for (String table : List.of("change_request", "impact_analysis_run", "impact_finding", "review_task"))
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        return counts;
    }

    private static Map<String, Object> historicalFormulaContent(Map<String, Object> row) {
        var content = new LinkedHashMap<>(row);
        content.remove("is_current_released");
        content.remove("current_formula_product_id");
        return content;
    }

    private static Map<String, Object> itemContent(Map<String, Object> row) {
        var content = new LinkedHashMap<>(row);
        content.remove("formula_item_id");
        content.remove("formula_version_id");
        return content;
    }

    private record SeedState(Map<String, Object> product, List<Map<String, Object>> formulas,
                             List<Map<String, Object>> items, List<Map<String, Object>> labels,
                             List<Map<String, Object>> declarations) { }

    private static Set<String> productsWith(
            Map<String, ProductImpactAssessment> byProduct, ImpactClassification classification) {
        return byProduct.values().stream()
                .filter(assessment -> assessment.classification() == classification)
                .map(ProductImpactAssessment::productId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private ChangeRequest change() {
        return new ChangeRequest("cr-scrum56", "CR-scrum56", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED,
                Instant.parse("2026-10-01T00:00:00Z"), "user_change_manager",
                "Chocolate Base Spec V2 adds Soy Lecithin", new VersionChange(SPEC_V1, targetSpecId), "prov_scenario_input");
    }

    /** Expected product sets from M2's versioned golden (S3-M2-SOY-SPEC-V2-IMPACT v1). */
    private static Map<String, Set<String>> golden() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                IngredientSpecImpactStrategyMySqlTest.class.getResourceAsStream(GOLDEN), GOLDEN),
                StandardCharsets.UTF_8))) {
            Map<String, Set<String>> byOutcome = reader.lines().skip(1)
                    .map(line -> line.split(",", -1))
                    .collect(Collectors.groupingBy(columns -> columns[9],
                            Collectors.mapping(columns -> columns[10], Collectors.toCollection(TreeSet::new))));
            assertThat(byOutcome.keySet()).containsExactlyInAnyOrder("NO_ACTION", "REVIEW_REQUIRED", "EXCLUDED_NO_FINDING");
            assertThat(byOutcome.values()).allSatisfy(products -> assertThat(products).hasSize(20));
            return byOutcome;
        }
    }
}
