package com.spectrace;

import com.spectrace.catalog.infrastructure.CatalogStore;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.MATERIAL;
import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Real FK locking: specification creation and adoption must both commit for the same material. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class FormulaAdoptionMaterialLockMySqlTest extends MySqlIntegrationTestSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROVENANCE = "prov_scenario_input";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private CatalogStore store;

    private String productId;
    private String sourceId;
    private String materialId;
    private String previousSpecId;
    private String targetSpecId;
    private String labelId;
    private HttpClient client;
    private CountDownLatch allowSpecificationAllocation;

    @BeforeEach
    void createCommittedIndependentProductAndLatestTargetSpecification() {
        String token = UUID.randomUUID().toString();
        productId = "scrum56-lock-product-" + token;
        sourceId = "scrum56-lock-formula-" + token;
        materialId = "scrum56-lock-material-" + token;
        previousSpecId = "scrum56-lock-spec-v1-" + token;
        targetSpecId = "scrum56-lock-spec-v2-" + token;
        labelId = "scrum56-lock-label-" + token;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        allowSpecificationAllocation = new CountDownLatch(1);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("""
                    INSERT INTO product(product_id,fdc_id,brand_owner,product_description,normalized_category,
                      source_ingredients_text,source_type,fixture_group,data_provenance_id)
                    VALUES (?,?,'SCRUM-56 lock fixture','Concurrent specification creation and adoption','TEST',
                      'Cocoa','PROJECT_SEEDED','NO_ACTION_BASELINE_SOY','prov_project_seed')
                    """, productId, -Math.abs(UUID.randomUUID().getMostSignificantBits()));
            jdbc.update("""
                    INSERT INTO supplier_material(supplier_material_id,supplier_id,ingredient_id,material_code,
                      material_name,material_description,data_provenance_id)
                    SELECT ?,supplier_id,ingredient_id,?,'SCRUM-56 lock material',material_description,data_provenance_id
                    FROM supplier_material WHERE supplier_material_id='mat_chocolate_base'
                    """, materialId, "SCRUM56-LOCK-" + token);
            jdbc.update("""
                    INSERT INTO ingredient_specification_version(specification_version_id,supplier_material_id,
                      version_number,lifecycle_status,effective_date,released_at,created_by_user_id,data_provenance_id)
                    VALUES (?, ?, 1, 'RELEASED', '2020-01-01', '2020-01-01 09:00:00','user_admin','prov_project_seed'),
                           (?, ?, 2, 'RELEASED', '2020-01-01', '2020-01-02 09:00:00','user_admin',?)
                    """, previousSpecId, materialId, targetSpecId, materialId, PROVENANCE);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id,specification_version_id,ingredient_id,
                      raw_phrase,match_rule,match_status,sequence_no)
                    VALUES (?,?,'ing_cocoa','Cocoa','SCRUM-56 V1 fixture','MATCHED',1),
                           (?,?,'ing_cocoa','Cocoa','SCRUM-56 V2 fixture','MATCHED',1)
                    """, "scrum56-lock-component-v1-" + token, previousSpecId,
                    "scrum56-lock-component-v2-" + token, targetSpecId);
            jdbc.update("""
                    INSERT INTO formula_version(formula_version_id,product_id,version_number,lifecycle_status,
                      is_current_released,created_by_user_id,released_by_user_id,released_at,data_provenance_id)
                    VALUES (?, ?, 1, 'RELEASED','Y','user_admin','user_admin','2026-01-01 09:00:00','prov_project_seed')
                    """, sourceId, productId);
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id,formula_version_id,supplier_material_id,
                      specification_version_id,sequence_no,quantity_value,quantity_unit)
                    VALUES (?,?,?,?,3,1.2500,'kg')
                    """, "scrum56-lock-item-" + token, sourceId, materialId, previousSpecId);
            jdbc.update("""
                    INSERT INTO label_version(label_version_id,product_id,formula_version_id,rule_set_version_id,
                      jurisdiction_code,version_number,raw_ingredient_text,lifecycle_status,is_current_published,
                      created_by_user_id,created_at,data_provenance_id)
                    SELECT ?,?,?,rule_set_version_id,jurisdiction_code,1,raw_ingredient_text,'PUBLISHED','Y',
                      created_by_user_id,created_at,data_provenance_id
                    FROM label_version WHERE label_version_id='label_1106285_v1'
                    """, labelId, productId, sourceId);
            jdbc.update("""
                    UPDATE product SET current_formula_version_id=?,current_published_label_version_id=? WHERE product_id=?
                    """, sourceId, labelId, productId);
        });
    }

    @AfterEach
    void releaseCoordinationAndRemoveOnlyThisTestsFixtures() {
        if (allowSpecificationAllocation != null) allowSpecificationAllocation.countDown();
        if (client != null) client.close();
        CatalogStore spyTarget = AopTestUtils.getUltimateTargetObject(store);
        reset(spyTarget);
        if (productId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("UPDATE product SET current_formula_version_id=NULL,current_published_label_version_id=NULL WHERE product_id=?", productId);
            jdbc.update("""
                    DELETE FROM audit_event WHERE entity_id IN
                      (SELECT formula_version_id FROM formula_version WHERE product_id=?)
                    OR entity_id IN
                      (SELECT specification_version_id FROM ingredient_specification_version WHERE supplier_material_id=?)
                    """, productId, materialId);
            jdbc.update("DELETE FROM label_version WHERE product_id=?", productId);
            jdbc.update("DELETE FROM formula_item WHERE formula_version_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=?)", productId);
            jdbc.update("DELETE FROM formula_version WHERE product_id=?", productId);
            jdbc.update("DELETE FROM product WHERE product_id=?", productId);
            jdbc.update("DELETE FROM spec_component WHERE specification_version_id IN (SELECT specification_version_id FROM ingredient_specification_version WHERE supplier_material_id=?)", materialId);
            jdbc.update("DELETE FROM ingredient_specification_version WHERE supplier_material_id=?", materialId);
            jdbc.update("DELETE FROM supplier_material WHERE supplier_material_id=?", materialId);
        });
    }

    @Test
    void creatingTheNextSpecificationAndAdoptingTheReleasedTargetBothCommit() throws Exception {
        var historicalFormula = formula(sourceId);
        var historicalItems = items(sourceId);
        var historicalLabel = jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", labelId);
        var targetSpecification = jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecId);
        var adoptionReachedCopy = new CountDownLatch(1);
        CatalogStore spyTarget = AopTestUtils.getUltimateTargetObject(store);
        doAnswer(invocation -> {
            // On the old implementation, all spec locks are held before this method's FK INSERT.
            adoptionReachedCopy.countDown();
            return invocation.callRealMethod();
        }).when(spyTarget).adoptFormula(eq(productId), eq(sourceId), eq(materialId),
                eq(targetSpecId), eq(PROVENANCE), eq("user_admin"));

        var responses = withConcurrentSpecificationCreation("/api/catalog/products/" + productId + "/formula-adoptions", """
                {"sourceFormulaVersionId":"%s","targetSpecificationVersionId":"%s"}
                """.formatted(sourceId, targetSpecId), adoptionReachedCopy);
        assertBothCreated(responses);
        var specificationResponse = responses.specification();
        var adoptionResponse = responses.formula();
        String createdSpecId = JSON.readTree(specificationResponse.body()).get("specification_version_id").stringValue();
        String adoptedFormulaId = JSON.readTree(adoptionResponse.body()).get("formula_version_id").stringValue();

        assertThat(jdbc.queryForList("SELECT version_number FROM ingredient_specification_version WHERE supplier_material_id=? ORDER BY version_number", Integer.class, materialId))
                .containsExactly(1, 2, 3);
        assertThat(jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", createdSpecId))
                .containsEntry("version_number", 3).containsEntry("lifecycle_status", "DRAFT")
                .containsEntry("supplier_material_id", materialId).containsEntry("created_by_user_id", "user_admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_component WHERE specification_version_id=?", Integer.class, createdSpecId)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecId)).isEqualTo(targetSpecification);
        assertThat(jdbc.queryForList("SELECT version_number FROM formula_version WHERE product_id=? ORDER BY version_number", Integer.class, productId))
                .containsExactly(1, 2);
        assertThat(formula(adoptedFormulaId)).containsEntry("version_number", 2)
                .containsEntry("lifecycle_status", "RELEASED").containsEntry("is_current_released", "Y")
                .containsEntry("created_by_user_id", "user_admin").containsEntry("released_by_user_id", "user_admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_version WHERE product_id=? AND is_current_released='Y'", Integer.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT current_formula_version_id,current_published_label_version_id FROM product WHERE product_id=?", productId))
                .containsEntry("current_formula_version_id", adoptedFormulaId).containsEntry("current_published_label_version_id", labelId);
        assertThat(historicalContent(formula(sourceId))).isEqualTo(historicalContent(historicalFormula));
        assertThat(items(sourceId)).isEqualTo(historicalItems);
        assertThat(jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", labelId)).isEqualTo(historicalLabel);
        var copiedItems = items(adoptedFormulaId);
        assertThat(copiedItems).hasSize(1);
        assertThat(copiedItems.getFirst()).containsEntry("supplier_material_id", materialId)
                .containsEntry("specification_version_id", targetSpecId).containsEntry("sequence_no", 3)
                .containsEntry("quantity_unit", "kg");
        assertThat((BigDecimal) copiedItems.getFirst().get("quantity_value")).isEqualByComparingTo("1.2500");
        assertThat(copiedItems.getFirst().get("formula_item_id")).isNotEqualTo(historicalItems.getFirst().get("formula_item_id"));
        assertThat(jdbc.queryForList("SELECT event_type FROM audit_event WHERE entity_id=?", String.class, adoptedFormulaId))
                .containsExactlyInAnyOrder("FORMULA_CREATED", "FORMULA_RELEASED", "FORMULA_SPECIFICATION_ADOPTED");
        assertThat(jdbc.queryForList("SELECT event_type FROM audit_event WHERE entity_id=?", String.class, createdSpecId))
                .containsExactly("SPECIFICATION_CREATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id IN (?,?) AND actor_user_id='user_admin' AND data_provenance_id=?", Integer.class, adoptedFormulaId, createdSpecId, PROVENANCE)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.targetSpecificationVersionId'))
                FROM audit_event WHERE entity_id=? AND event_type='FORMULA_SPECIFICATION_ADOPTED'
                """, String.class, adoptedFormulaId)).isEqualTo(targetSpecId);
    }

    @Test
    void creatingAFormulaAndTheNextSpecificationBothCommit() throws Exception {
        var historicalFormula = formula(sourceId);
        var historicalItems = items(sourceId);
        var historicalLabel = jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", labelId);
        var formulaReachedInsert = new CountDownLatch(1);
        CatalogStore spyTarget = AopTestUtils.getUltimateTargetObject(store);
        doAnswer(invocation -> {
            formulaReachedInsert.countDown();
            return invocation.callRealMethod();
        }).when(spyTarget).formula(argThat(value -> value != null && productId.equals(value.productId())), eq("user_admin"));

        var responses = withConcurrentSpecificationCreation("/api/catalog/formulas", """
                {"productId":"%s","provenanceId":"%s",
                 "items":[{"materialId":"%s","specificationId":"%s","quantity":1.2500,"unit":"kg"}]}
                """.formatted(productId, PROVENANCE, materialId, targetSpecId), formulaReachedInsert);
        assertBothCreated(responses);
        String createdSpecId = JSON.readTree(responses.specification().body()).get("specification_version_id").stringValue();
        String createdFormulaId = JSON.readTree(responses.formula().body()).get("formula_version_id").stringValue();

        assertThat(jdbc.queryForList("SELECT version_number FROM ingredient_specification_version WHERE supplier_material_id=? ORDER BY version_number", Integer.class, materialId))
                .containsExactly(1, 2, 3);
        assertThat(jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", createdSpecId))
                .containsEntry("version_number", 3).containsEntry("lifecycle_status", "DRAFT")
                .containsEntry("created_by_user_id", "user_admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_component WHERE specification_version_id=?", Integer.class, createdSpecId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT version_number FROM formula_version WHERE product_id=? ORDER BY version_number", Integer.class, productId))
                .containsExactly(1, 2);
        assertThat(formula(createdFormulaId)).containsEntry("lifecycle_status", "DRAFT")
                .containsEntry("is_current_released", "N").containsEntry("created_by_user_id", "user_admin")
                .containsEntry("released_by_user_id", null).containsEntry("released_at", null);
        assertThat(jdbc.queryForMap("SELECT current_formula_version_id,current_published_label_version_id FROM product WHERE product_id=?", productId))
                .containsEntry("current_formula_version_id", sourceId).containsEntry("current_published_label_version_id", labelId);
        assertThat(formula(sourceId)).isEqualTo(historicalFormula);
        assertThat(items(sourceId)).isEqualTo(historicalItems);
        assertThat(jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", labelId)).isEqualTo(historicalLabel);
        var createdItems = items(createdFormulaId);
        assertThat(createdItems).hasSize(1);
        assertThat(createdItems.getFirst()).containsEntry("supplier_material_id", materialId)
                .containsEntry("specification_version_id", targetSpecId).containsEntry("sequence_no", 1)
                .containsEntry("quantity_unit", "kg");
        assertThat((BigDecimal) createdItems.getFirst().get("quantity_value")).isEqualByComparingTo("1.2500");
        assertThat(jdbc.queryForList("SELECT event_type FROM audit_event WHERE entity_id=?", String.class, createdFormulaId))
                .containsExactly("FORMULA_CREATED");
        assertThat(jdbc.queryForList("SELECT event_type FROM audit_event WHERE entity_id=?", String.class, createdSpecId))
                .containsExactly("SPECIFICATION_CREATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id IN (?,?) AND actor_user_id='user_admin' AND data_provenance_id=?", Integer.class, createdFormulaId, createdSpecId, PROVENANCE)).isEqualTo(2);
    }

    private ConcurrentResponses withConcurrentSpecificationCreation(String formulaPath, String formulaBody,
                                                                    CountDownLatch formulaReachedWrite) throws Exception {
        var materialLockedByCreator = new CountDownLatch(1);
        var productLockedByFormula = new CountDownLatch(1);
        var creatorGateUsed = new AtomicBoolean();
        CatalogStore spyTarget = AopTestUtils.getUltimateTargetObject(store);
        doAnswer(invocation -> {
            // The first real material locking read belongs to the creator, which is started first.
            boolean creator = creatorGateUsed.compareAndSet(false, true);
            Object result = invocation.callRealMethod();
            if (creator) {
                materialLockedByCreator.countDown();
                assertThat(allowSpecificationAllocation.await(10, TimeUnit.SECONDS))
                        .as("bounded creator coordination was explicitly released").isTrue();
            }
            return result;
        }).when(spyTarget).get(eq(MATERIAL), eq(materialId), eq(true));
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            productLockedByFormula.countDown();
            return result;
        }).when(spyTarget).get(eq(PRODUCT), eq(productId), eq(true));
        var specification = post("/api/catalog/specifications", """
                {"materialId":"%s","effectiveDate":"2020-01-01","provenanceId":"%s",
                 "components":[{"ingredientId":"ing_cocoa","rawPhrase":"Cocoa",
                                "matchRule":"SCRUM-56 concurrent V3"}]}
                """.formatted(materialId, PROVENANCE));
        try {
            assertThat(materialLockedByCreator.await(5, TimeUnit.SECONDS))
                    .as("specification creator acquired the real material lock").isTrue();
            var formula = post(formulaPath, formulaBody);
            assertThat(productLockedByFormula.await(5, TimeUnit.SECONDS))
                    .as("competing formula request reached its real product locking read").isTrue();
            // A fixed request can wait on material before reaching its write stage. Observing the
            // write stage is optional; its absence must never prevent the creator from proceeding.
            boolean reachedWrite = formulaReachedWrite.await(2, TimeUnit.SECONDS);
            allowSpecificationAllocation.countDown();
            return new ConcurrentResponses(specification.get(20, TimeUnit.SECONDS),
                    formula.get(20, TimeUnit.SECONDS), reachedWrite);
        } finally {
            allowSpecificationAllocation.countDown();
        }
    }

    private static void assertBothCreated(ConcurrentResponses responses) {
        assertThat(List.of(responses.specification().statusCode(), responses.formula().statusCode()))
                .as("both independent valid requests commit; write while material held=%s; creator=%s; formula=%s",
                        responses.reachedWrite(), responses.specification().body(), responses.formula().body())
                .containsExactly(201, 201);
    }

    private record ConcurrentResponses(HttpResponse<String> specification, HttpResponse<String> formula,
                                       boolean reachedWrite) { }

    private CompletableFuture<HttpResponse<String>> post(String path, String body) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "dev-external-admin")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> formula(String id) { return jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", id); }
    private List<Map<String, Object>> items(String id) { return jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY sequence_no", id); }

    private static Map<String, Object> historicalContent(Map<String, Object> formula) {
        var content = new LinkedHashMap<>(formula);
        content.remove("is_current_released");
        content.remove("current_formula_product_id");
        return content;
    }
}
