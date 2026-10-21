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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** S4 batch adoption acceptance against the real V3 seed and MySQL transactions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class BatchSpecificationAdoptionMySqlTest extends MySqlIntegrationTestSupport {
    private static final String MATERIAL = "mat_chocolate_base";
    private static final String ADMIN = "dev-external-admin";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private CatalogStore store;

    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String targetSpecificationId = "s4-batch-spec-v2-" + token;
    private final String changedFormulaId = "s4-batch-formula-current-" + token;
    private HttpClient client;
    private List<String> candidates;
    private Map<String, String> originalCurrentFormulas;
    private String changedProductId;
    private String failedProductId;
    private AtomicBoolean switchCurrentFormula;
    private AtomicBoolean failReplayMarker;

    @BeforeEach
    void createReleasedTargetAndFormulaThatBecomesCurrentAfterDiscovery() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("""
                    INSERT INTO ingredient_specification_version(specification_version_id,supplier_material_id,
                      version_number,lifecycle_status,effective_date,released_at,created_by_user_id,data_provenance_id)
                    VALUES (?,? ,2,'RELEASED','2026-10-13',UTC_TIMESTAMP(),'user_admin','prov_project_seed')
                    """, targetSpecificationId, MATERIAL);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id,specification_version_id,ingredient_id,
                      raw_phrase,match_rule,match_status,sequence_no)
                    SELECT CONCAT('s4-batch-', ?, '-', sequence_no), ?, ingredient_id, raw_phrase,
                           match_rule, match_status, sequence_no
                    FROM spec_component WHERE specification_version_id='spec_chocolate_v1'
                    """, token, targetSpecificationId);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id,specification_version_id,ingredient_id,
                      raw_phrase,match_rule,match_status,sequence_no)
                    VALUES (?,?,'ing_soy_lecithin','Soy lecithin','S4 batch MySQL fixture','MATCHED',99)
                    """, "s4-batch-soy-" + token, targetSpecificationId);

            candidates = jdbc.queryForList("""
                    SELECT DISTINCT p.product_id FROM product p
                    JOIN formula_version fv ON fv.formula_version_id=p.current_formula_version_id
                      AND fv.product_id=p.product_id AND fv.lifecycle_status='RELEASED'
                      AND fv.is_current_released='Y'
                    JOIN formula_item fi ON fi.formula_version_id=fv.formula_version_id
                      AND fi.supplier_material_id=? ORDER BY p.product_id
                    """, String.class, MATERIAL);
            originalCurrentFormulas = new LinkedHashMap<>();
            for (String productId : candidates) {
                originalCurrentFormulas.put(productId, jdbc.queryForObject(
                        "SELECT current_formula_version_id FROM product WHERE product_id=?", String.class, productId));
            }
            changedProductId = candidates.getFirst();
            failedProductId = candidates.get(1);
            String oldFormulaId = originalCurrentFormulas.get(changedProductId);
            jdbc.update("""
                    INSERT INTO formula_version(formula_version_id,product_id,version_number,lifecycle_status,
                      is_current_released,created_by_user_id,released_by_user_id,released_at,data_provenance_id)
                    SELECT ?,product_id,version_number+1,'RELEASED','N','user_admin','user_admin',
                           UTC_TIMESTAMP(),'prov_project_seed'
                    FROM formula_version WHERE formula_version_id=?
                    """, changedFormulaId, oldFormulaId);
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id,formula_version_id,supplier_material_id,
                      specification_version_id,sequence_no,quantity_value,quantity_unit)
                    SELECT CONCAT('s4-batch-fi-', ?, '-', sequence_no), ?, supplier_material_id,
                           specification_version_id,sequence_no,quantity_value,quantity_unit
                    FROM formula_item WHERE formula_version_id=?
                    """, token, changedFormulaId, oldFormulaId);
        });
        assertThat(candidates).hasSize(40);
        switchCurrentFormula = new AtomicBoolean(true);
        failReplayMarker = new AtomicBoolean(true);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) invocation.callRealMethod();
            if (MATERIAL.equals(invocation.getArgument(0)) && switchCurrentFormula.compareAndSet(true, false)) {
                new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                    jdbc.update("UPDATE formula_version SET is_current_released='N' WHERE product_id=? AND is_current_released='Y'",
                            changedProductId);
                    jdbc.update("UPDATE formula_version SET is_current_released='Y' WHERE formula_version_id=?", changedFormulaId);
                    jdbc.update("UPDATE product SET current_formula_version_id=? WHERE product_id=?", changedFormulaId, changedProductId);
                });
            }
            return rows;
        }).when(store).currentProductsUsingMaterial(anyString());
        doAnswer(invocation -> {
            String productId = invocation.getArgument(0);
            if (failedProductId.equals(productId) && failReplayMarker.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated replay marker insert failure");
            }
            return invocation.callRealMethod();
        }).when(store).recordBatchAdoption(anyString(), anyString(), eq(targetSpecificationId), anyString());
    }

    @AfterEach
    void removeOnlyThisTestBatchAndItsFormulaSnapshots() {
        CatalogStore storeTarget = AopTestUtils.getUltimateTargetObject(store);
        reset(storeTarget);
        if (client != null) client.close();
        if (candidates == null || originalCurrentFormulas == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("DELETE FROM formula_adoption_batch_item WHERE target_specification_version_id=?", targetSpecificationId);
            for (String productId : candidates) {
                String original = originalCurrentFormulas.get(productId);
                jdbc.update("UPDATE product SET current_formula_version_id=? WHERE product_id=?", original, productId);
                jdbc.update("UPDATE formula_version SET is_current_released='N' WHERE product_id=?", productId);
                jdbc.update("UPDATE formula_version SET is_current_released='Y' WHERE formula_version_id=?", original);
                jdbc.update("DELETE FROM audit_event WHERE entity_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=? AND formula_version_id<>?)",
                        productId, original);
                jdbc.update("DELETE FROM formula_item WHERE formula_version_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=? AND formula_version_id<>?)",
                        productId, original);
                jdbc.update("DELETE FROM formula_version WHERE product_id=? AND formula_version_id<>?", productId, original);
            }
            jdbc.update("DELETE FROM spec_component WHERE specification_version_id=?", targetSpecificationId);
            jdbc.update("DELETE FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecificationId);
        });
    }

    /**
     * Cai's review of PR #91: a competing transaction commits a new current formula while the
     * batch waits for the product lock. Under REPEATABLE READ the batch must not have opened its
     * snapshot before that lock, or the new formula's items are invisible and adoption fails.
     */
    @Test
    void aFormulaCommittedBeforeTheProductLockIsAdoptedWithItsItems() throws Exception {
        switchCurrentFormula.set(false);
        failReplayMarker.set(false);
        String racedProductId = candidates.get(2);
        String racedFormulaId = "s4-batch-race-formula-" + token;
        AtomicBoolean commitCompetingFormula = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (commitCompetingFormula.compareAndSet(true, false)) {
                // A separate, committed transaction, interleaved inside the batch's product transaction.
                TransactionTemplate competing = new TransactionTemplate(transactionManager);
                competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                competing.executeWithoutResult(transaction ->
                        makeCurrent(racedProductId, racedFormulaId, "s4-batch-race-fi-" + token + "-"));
            }
            return invocation.callRealMethod();
        }).when(store).get(eq(CatalogStore.Kind.PRODUCT), eq(racedProductId), eq(true));

        JsonNode summary = postBatch();

        JsonNode raced = result(summary, racedProductId);
        assertThat(raced.path("status").stringValue()).as(raced.toString()).isEqualTo("ADOPTED");
        assertThat(summary.path("failedCount").intValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT source_formula_version_id FROM formula_adoption_batch_item WHERE product_id=? AND target_specification_version_id=?",
                String.class, racedProductId, targetSpecificationId)).isEqualTo(racedFormulaId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_item WHERE formula_version_id=? AND specification_version_id=?",
                Integer.class, raced.path("formulaVersionId").stringValue(), targetSpecificationId)).isEqualTo(1);
    }

    /** A released copy of the product's current formula, with its items, made current. */
    private void makeCurrent(String productId, String formulaId, String itemPrefix) {
        String current = jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?",
                String.class, productId);
        jdbc.update("""
                INSERT INTO formula_version(formula_version_id,product_id,version_number,lifecycle_status,
                  is_current_released,created_by_user_id,released_by_user_id,released_at,data_provenance_id)
                SELECT ?,product_id,version_number+1,'RELEASED','N','user_admin','user_admin',
                       UTC_TIMESTAMP(),'prov_project_seed'
                FROM formula_version WHERE formula_version_id=?
                """, formulaId, current);
        jdbc.update("""
                INSERT INTO formula_item(formula_item_id,formula_version_id,supplier_material_id,
                  specification_version_id,sequence_no,quantity_value,quantity_unit)
                SELECT CONCAT(?, sequence_no), ?, supplier_material_id,
                       specification_version_id,sequence_no,quantity_value,quantity_unit
                FROM formula_item WHERE formula_version_id=?
                """, itemPrefix, formulaId, current);
        jdbc.update("UPDATE formula_version SET is_current_released='N' WHERE product_id=? AND is_current_released='Y'", productId);
        jdbc.update("UPDATE formula_version SET is_current_released='Y' WHERE formula_version_id=?", formulaId);
        jdbc.update("UPDATE product SET current_formula_version_id=? WHERE product_id=?", formulaId, productId);
    }

    @Test
    void batchAdoptionUsesTheLatestCurrentFormulaRollsBackOneFailureAndReplaysTheOtherProducts() throws Exception {
        int failedFormulaCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM formula_version WHERE product_id=?", Integer.class, failedProductId);
        String failedSourceBefore = originalCurrentFormulas.get(failedProductId);

        JsonNode first = postBatch();

        assertThat(first.path("candidateCount").intValue()).isEqualTo(40);
        assertThat(first.path("adoptedCount").intValue()).isEqualTo(39);
        assertThat(first.path("skippedCount").intValue()).isZero();
        assertThat(first.path("failedCount").intValue()).isEqualTo(1);
        assertThat(result(first, changedProductId).path("status").stringValue()).isEqualTo("ADOPTED");
        assertThat(jdbc.queryForObject("SELECT source_formula_version_id FROM formula_adoption_batch_item WHERE product_id=? AND target_specification_version_id=?",
                String.class, changedProductId, targetSpecificationId)).isEqualTo(changedFormulaId);
        assertThat(jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?", String.class, changedProductId))
                .isEqualTo(result(first, changedProductId).path("formulaVersionId").stringValue());

        JsonNode failed = result(first, failedProductId);
        assertThat(failed.path("status").stringValue()).isEqualTo("FAILED");
        assertThat(failed.path("errorCode").stringValue()).isEqualTo("ADOPTION_FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_adoption_batch_item WHERE product_id=? AND target_specification_version_id=?",
                Integer.class, failedProductId, targetSpecificationId)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?", String.class, failedProductId))
                .isEqualTo(failedSourceBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_version WHERE product_id=?", Integer.class, failedProductId))
                .isEqualTo(failedFormulaCountBefore);

        JsonNode replay = postBatch();

        assertThat(replay.path("candidateCount").intValue()).isEqualTo(40);
        assertThat(replay.path("adoptedCount").intValue()).isEqualTo(40);
        assertThat(replay.path("skippedCount").intValue()).isZero();
        assertThat(replay.path("failedCount").intValue()).isZero();
        assertThat(replay.path("results").isArray()).isTrue();
        List<String> replayed = new ArrayList<>();
        replay.path("results").forEach(row -> { if (row.path("replayed").booleanValue()) replayed.add(row.path("productId").stringValue()); });
        assertThat(replayed).hasSize(39);
        assertThat(replayed).doesNotContain(failedProductId);
        assertThat(result(replay, failedProductId).path("replayed").booleanValue()).isFalse();
    }

    private JsonNode postBatch() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/catalog/specifications/" + targetSpecificationId + "/formula-adoptions"))
                .timeout(Duration.ofSeconds(30)).header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", ADMIN).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static JsonNode result(JsonNode summary, String productId) {
        for (JsonNode row : summary.path("results")) {
            if (productId.equals(row.path("productId").stringValue())) return row;
        }
        throw new AssertionError("No batch adoption result for " + productId);
    }
}
