package com.spectrace.label;

import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class LabelDraftDeclarationsHttpMySqlTest extends MySqlIntegrationTestSupport {
    private static final String PRODUCT = "prod_usda_1106285";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ReviewTaskDraftBinding binding;
    private final List<String> createdLabels = new ArrayList<>();

    @AfterEach
    void cleanUpOnlyLabelsCreatedByThisTest() {
        reset(binding);
        for (String id : createdLabels) {
            jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", id);
            jdbc.update("DELETE FROM label_version WHERE label_version_id = ?", id);
        }
        createdLabels.clear();
    }

    @Test
    void firstHttpCreateStoresExactUserEnteredDeclarationsAndPreservesHistory() throws Exception {
        var historicalLabels = jdbc.queryForList("SELECT * FROM label_version ORDER BY label_version_id");
        var historicalDeclarations = jdbc.queryForList("SELECT * FROM label_allergen_declaration ORDER BY label_allergen_declaration_id");
        var response = create("""
                [{"allergenId":"all_soy","declarationType":"CONTAINS","displayText":"Contains soy"},
                 {"allergenId":"all_wheat","declarationType":"CONTAINS","displayText":"Contains wheat"}]
                """);
        assertEquals(201, response.statusCode(), response.body());
        String id = JSON.readTree(response.body()).get("labelVersionId").stringValue();
        createdLabels.add(id);
        var stored = jdbc.queryForList("""
                SELECT allergen_id, declaration_type, declaration_source, display_text
                FROM label_allergen_declaration WHERE label_version_id = ? ORDER BY allergen_id
                """, id);
        assertEquals(2, stored.size());
        assertEquals("all_soy", stored.get(0).get("allergen_id"));
        assertEquals("Contains soy", stored.get(0).get("display_text"));
        assertEquals("all_wheat", stored.get(1).get("allergen_id"));
        assertEquals("Contains wheat", stored.get(1).get("display_text"));
        stored.forEach(row -> {
            assertEquals("CONTAINS", row.get("declaration_type"));
            assertEquals("USER_ENTERED", row.get("declaration_source"));
        });
        var read = send("/api/labels/" + id + "/declarations", null);
        assertEquals(200, read.statusCode(), read.body());
        assertEquals(id, JSON.readTree(read.body()).get("labelVersionId").stringValue());
        assertEquals(2, JSON.readTree(read.body()).get("declarations").size());
        assertEquals(historicalLabels, jdbc.queryForList(
                "SELECT * FROM label_version WHERE label_version_id <> ? ORDER BY label_version_id", id));
        assertEquals(historicalDeclarations, jdbc.queryForList(
                "SELECT * FROM label_allergen_declaration WHERE label_version_id <> ? ORDER BY label_allergen_declaration_id", id));
    }

    @Test
    void unknownCatalogIdRollsBackWithoutAnyDraftDeclarationOrTaskChanges() throws Exception {
        var before = allBusinessRows();
        var response = create("[{\"allergenId\":\"unknown_allergen\",\"declarationType\":\"CONTAINS\"}]");
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("LABEL_DRAFT_INVALID", JSON.readTree(response.body()).get("code").stringValue());
        assertEquals(before, allBusinessRows());
    }

    @Test
    void declarationFromAnotherJurisdictionCannotBeClaimedForThisDraft() throws Exception {
        var before = allBusinessRows();
        var response = send("/api/labels/drafts",
                "{\"productId\":\"" + PRODUCT + "\",\"jurisdictionCode\":\"SG\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"}]}");
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("LABEL_DRAFT_INVALID", JSON.readTree(response.body()).get("code").stringValue());
        assertEquals(before, allBusinessRows());
    }

    @Test
    void duplicateDeclarationsAreRejectedBeforeAnyWrite() throws Exception {
        var before = allBusinessRows();
        var response = create("""
                [{"allergenId":"all_soy","declarationType":"CONTAINS"},
                 {"allergenId":"ALL_SOY","declarationType":"CONTAINS"}]
                """);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("LABEL_DRAFT_INVALID", JSON.readTree(response.body()).get("code").stringValue());
        assertEquals(before, allBusinessRows());
    }

    @Test
    void failedBindingAfterDeclarationInsertsRollsBackTheWholeHttpCommand() throws Exception {
        var before = allBusinessRows();
        doThrow(new DataIntegrityViolationException("Simulated task binding persistence failure"))
                .when(binding).bindOpenTaskToDraft(eq(PRODUCT), eq("US"), anyString(), isNull());
        var response = create("[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"}]");
        assertEquals(500, response.statusCode(), response.body());
        assertEquals("INTERNAL_ERROR", JSON.readTree(response.body()).get("code").stringValue());
        assertFalse(response.body().contains("Simulated"));
        assertEquals(before, allBusinessRows());
    }

    @Test
    void invalidTargetTaskReturnsConflictWithoutAPlaceholderDraft() throws Exception {
        var before = allBusinessRows();
        var response = send("/api/labels/drafts",
                "{\"productId\":\"" + PRODUCT + "\",\"jurisdictionCode\":\"US\",\"reviewTaskId\":\"missing_task\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"}]}");
        assertEquals(409, response.statusCode(), response.body());
        assertEquals("LABEL_VERSION_CONFLICT", JSON.readTree(response.body()).get("code").stringValue());
        assertEquals(before, allBusinessRows());
    }

    private List<List<java.util.Map<String, Object>>> allBusinessRows() {
        return List.of(jdbc.queryForList("SELECT * FROM label_version ORDER BY label_version_id"),
                jdbc.queryForList("SELECT * FROM label_allergen_declaration ORDER BY label_allergen_declaration_id"),
                jdbc.queryForList("SELECT * FROM review_task ORDER BY review_task_id"),
                jdbc.queryForList("SELECT * FROM audit_event ORDER BY audit_event_id"));
    }

    private HttpResponse<String> create(String declarations) throws Exception {
        return send("/api/labels/drafts", "{\"productId\":\"" + PRODUCT
                + "\",\"jurisdictionCode\":\"US\",\"declarations\":" + declarations + "}");
    }

    private HttpResponse<String> send(String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", "dev-external-label-officer");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void staleNPlusOneFindingCannotBindNPlusTwoCurrentFormulaWithOrWithoutExplicitTask() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String formulaN1 = "test_s3_n1_" + suffix;
        String formulaN2 = "test_s3_n2_" + suffix;
        String changeId = "test_s3_change_" + suffix;
        String runId = "test_s3_run_" + suffix;
        String findingId = "test_s3_finding_" + suffix;
        String taskId = "test_s3_task_" + suffix;
        String originalFormula = jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id = ?", String.class, PRODUCT);
        String publishedLabel = jdbc.queryForObject("SELECT current_published_label_version_id FROM product WHERE product_id = ?", String.class, PRODUCT);
        try {
            for (int increment = 1; increment <= 2; increment++) {
                jdbc.update("""
                        INSERT INTO formula_version (formula_version_id, product_id, version_number,
                            lifecycle_status, is_current_released, created_by_user_id,
                            released_by_user_id, released_at, data_provenance_id)
                        SELECT ?, product_id, version_number + ?, 'RELEASED', 'N',
                               created_by_user_id, released_by_user_id, NOW(), data_provenance_id
                        FROM formula_version WHERE formula_version_id = ?
                        """, increment == 1 ? formulaN1 : formulaN2, 9000 + increment, originalFormula);
            }
            jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", originalFormula);
            jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", formulaN2);
            jdbc.update("UPDATE product SET current_formula_version_id = ? WHERE product_id = ?", formulaN2, PRODUCT);
            jdbc.update("""
                    INSERT INTO change_request (change_request_id, change_request_code, change_type,
                        status, requested_at, requested_by_user_id, description,
                        from_formula_version_id, to_formula_version_id, data_provenance_id)
                    SELECT ?, ?, 'FORMULA', 'ANALYZED', NOW(), 'user_label_officer',
                           'Test-only stale first-draft binding fixture', ?, ?, data_provenance_id
                    FROM product WHERE product_id = ?
                    """, changeId, "code_" + suffix, originalFormula, formulaN1, PRODUCT);
            jdbc.update("""
                    INSERT INTO impact_analysis_run (impact_analysis_run_id, run_code, change_request_id,
                        idempotency_key, rule_set_version_id, status, started_at, completed_at,
                        executed_by_user_id, data_provenance_id)
                    SELECT ?, ?, ?, ?, rule_set_version_id, 'COMPLETED', NOW(), NOW(),
                           'user_label_officer', data_provenance_id
                    FROM label_version WHERE label_version_id = ?
                    """, runId, "code_run_" + suffix, changeId, "impact-analysis:" + changeId, publishedLabel);
            jdbc.update("""
                    INSERT INTO impact_finding (impact_finding_id, impact_analysis_run_id, product_id,
                        current_formula_version_id, proposed_formula_version_id, current_label_version_id,
                        classification, missing_allergen_codes, explanation, data_provenance_id)
                    SELECT ?, ?, product_id, formula_version_id, ?, label_version_id,
                           'REVIEW_REQUIRED', JSON_ARRAY('SOY'), 'Test-only N+1 finding', data_provenance_id
                    FROM label_version WHERE label_version_id = ?
                    """, findingId, runId, formulaN1, publishedLabel);
            jdbc.update("""
                    INSERT INTO review_task (review_task_id, impact_finding_id, product_id,
                        current_label_version_id, draft_label_version_id, target_label_version_id,
                        status, assigned_to_user_id, created_by_user_id, created_at, data_provenance_id)
                    SELECT ?, ?, product_id, current_published_label_version_id, NULL, NULL, 'OPEN',
                           'user_approver', 'user_label_officer', NOW(), data_provenance_id
                    FROM product WHERE product_id = ?
                    """, taskId, findingId, PRODUCT);
            var before = List.of(allBusinessRows(),
                    jdbc.queryForMap("SELECT * FROM product WHERE product_id = ?", PRODUCT),
                    jdbc.queryForList("SELECT * FROM formula_version WHERE product_id = ? ORDER BY formula_version_id", PRODUCT),
                    jdbc.queryForMap("SELECT * FROM impact_finding WHERE impact_finding_id = ?", findingId));
            for (boolean explicitTask : List.of(true, false)) {
                var response = send("/api/labels/drafts", "{\"productId\":\"" + PRODUCT
                        + "\",\"jurisdictionCode\":\"US\""
                        + (explicitTask ? ",\"reviewTaskId\":\"" + taskId + "\"" : "")
                        + ",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"}]}");
                assertEquals(409, response.statusCode(), response.body());
                assertEquals("LABEL_VERSION_CONFLICT", JSON.readTree(response.body()).get("code").stringValue());
                assertTrue(JSON.readTree(response.body()).get("message").stringValue().contains("impact finding"));
                assertEquals(before, List.of(allBusinessRows(),
                        jdbc.queryForMap("SELECT * FROM product WHERE product_id = ?", PRODUCT),
                        jdbc.queryForList("SELECT * FROM formula_version WHERE product_id = ? ORDER BY formula_version_id", PRODUCT),
                        jdbc.queryForMap("SELECT * FROM impact_finding WHERE impact_finding_id = ?", findingId)));
            }
        } finally {
            jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", taskId);
            jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", findingId);
            jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", runId);
            jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", changeId);
            jdbc.update("UPDATE product SET current_formula_version_id = ? WHERE product_id = ?", originalFormula, PRODUCT);
            jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", formulaN2);
            jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", originalFormula);
            jdbc.update("DELETE FROM formula_version WHERE formula_version_id IN (?, ?)", formulaN1, formulaN2);
        }
    }

    @Test
    void concurrentFirstCreatesHaveOneWinnerAndNeverRebindTheTaskOrCopyTheLosingPayload() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String changeId = "test_s3_race_change_" + suffix;
        String runId = "test_s3_race_run_" + suffix;
        String findingId = "test_s3_race_finding_" + suffix;
        String taskId = "test_s3_race_task_" + suffix;
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var firstLocked = new java.util.concurrent.CountDownLatch(1);
        var secondEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try {
            jdbc.update("""
                    INSERT INTO change_request (change_request_id, change_request_code, change_type,
                        status, requested_at, requested_by_user_id, description,
                        from_formula_version_id, to_formula_version_id, data_provenance_id)
                    SELECT ?, ?, 'FORMULA', 'ANALYZED', NOW(), 'user_label_officer',
                           'Test-only concurrent first-draft fixture',
                           current_formula_version_id, current_formula_version_id, data_provenance_id
                    FROM product WHERE product_id = ?
                    """, changeId, "code_" + suffix, PRODUCT);
            jdbc.update("""
                    INSERT INTO impact_analysis_run (impact_analysis_run_id, run_code, change_request_id,
                        idempotency_key, rule_set_version_id, status, started_at, completed_at,
                        executed_by_user_id, data_provenance_id)
                    SELECT ?, ?, ?, ?, lv.rule_set_version_id, 'COMPLETED', NOW(), NOW(),
                           'user_label_officer', p.data_provenance_id
                    FROM product p JOIN label_version lv ON lv.label_version_id = p.current_published_label_version_id
                    WHERE p.product_id = ?
                    """, runId, "run_" + suffix, changeId, "impact-analysis:" + changeId, PRODUCT);
            jdbc.update("""
                    INSERT INTO impact_finding (impact_finding_id, impact_analysis_run_id, product_id,
                        current_formula_version_id, proposed_formula_version_id, current_label_version_id,
                        classification, missing_allergen_codes, explanation, data_provenance_id)
                    SELECT ?, ?, product_id, formula_version_id, NULL, label_version_id,
                           'REVIEW_REQUIRED', JSON_ARRAY('SOY'), 'Legacy NULL-proposed race fixture', data_provenance_id
                    FROM label_version WHERE label_version_id =
                        (SELECT current_published_label_version_id FROM product WHERE product_id = ?)
                    """, findingId, runId, PRODUCT);
            jdbc.update("""
                    INSERT INTO review_task (review_task_id, impact_finding_id, product_id,
                        current_label_version_id, draft_label_version_id, target_label_version_id,
                        status, assigned_to_user_id, created_by_user_id, created_at, data_provenance_id)
                    SELECT ?, ?, product_id, current_published_label_version_id, NULL, NULL, 'OPEN',
                           'user_approver', 'user_label_officer', NOW(), data_provenance_id
                    FROM product WHERE product_id = ?
                    """, taskId, findingId, PRODUCT);
            var oldLabels = jdbc.queryForList("SELECT * FROM label_version ORDER BY label_version_id");
            var oldDeclarations = jdbc.queryForList("SELECT * FROM label_allergen_declaration ORDER BY label_allergen_declaration_id");
            var oldAudits = jdbc.queryForList("SELECT * FROM audit_event ORDER BY audit_event_id");
            var oldProduct = jdbc.queryForMap("SELECT * FROM product WHERE product_id = ?", PRODUCT);
            doAnswer(invocation -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 2) secondEntered.countDown();
                Object result = invocation.callRealMethod();
                if (attempt == 1) {
                    firstLocked.countDown();
                    if (!releaseFirst.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test timed out holding the first actual product/task lock");
                    }
                }
                return result;
            }).when(binding).requireFirstDraftAvailable(eq(PRODUCT), eq("US"), eq(taskId));
            java.util.function.Function<String, String> payload = display ->
                    "{\"productId\":\"" + PRODUCT + "\",\"jurisdictionCode\":\"US\",\"reviewTaskId\":\""
                            + taskId + "\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\",\"displayText\":\""
                            + display + "\"}]}";
            var first = executor.submit(() -> send("/api/labels/drafts", payload.apply("Contains soy winner")));
            assertTrue(firstLocked.await(20, java.util.concurrent.TimeUnit.SECONDS), "First real preflight did not hold its transaction lock");
            var second = executor.submit(() -> send("/api/labels/drafts", payload.apply("Contains soy loser")));
            assertTrue(secondEntered.await(20, java.util.concurrent.TimeUnit.SECONDS), "Second HTTP request did not enter the real transactional service");
            assertFalse(second.isDone(), "Competing create must wait while the first product transaction is held");
            releaseFirst.countDown();
            var won = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var lost = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(201, won.statusCode(), won.body());
            assertEquals(409, lost.statusCode(), lost.body());
            assertEquals("LABEL_VERSION_CONFLICT", JSON.readTree(lost.body()).get("code").stringValue());
            String id = JSON.readTree(won.body()).get("labelVersionId").stringValue();
            createdLabels.add(id);
            assertEquals(id, jdbc.queryForObject("SELECT draft_label_version_id FROM review_task WHERE review_task_id = ?", String.class, taskId));
            assertEquals(id, jdbc.queryForObject("SELECT target_label_version_id FROM review_task WHERE review_task_id = ?", String.class, taskId));
            assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM review_task WHERE review_task_id = ?", String.class, taskId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM label_allergen_declaration WHERE label_version_id = ?", Integer.class, id));
            assertEquals("Contains soy winner", jdbc.queryForObject("SELECT display_text FROM label_allergen_declaration WHERE label_version_id = ?", String.class, id));
            assertEquals("USER_ENTERED", jdbc.queryForObject("SELECT declaration_source FROM label_allergen_declaration WHERE label_version_id = ?", String.class, id));
            assertEquals(oldLabels, jdbc.queryForList("SELECT * FROM label_version WHERE label_version_id <> ? ORDER BY label_version_id", id));
            assertEquals(oldDeclarations, jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id <> ? ORDER BY label_allergen_declaration_id", id));
            assertEquals(oldAudits, jdbc.queryForList("SELECT * FROM audit_event ORDER BY audit_event_id"));
            assertEquals(oldProduct, jdbc.queryForMap("SELECT * FROM product WHERE product_id = ?", PRODUCT));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            reset(binding);
            jdbc.update("DELETE FROM review_task WHERE review_task_id = ?", taskId);
            jdbc.update("DELETE FROM impact_finding WHERE impact_finding_id = ?", findingId);
            jdbc.update("DELETE FROM impact_analysis_run WHERE impact_analysis_run_id = ?", runId);
            jdbc.update("DELETE FROM change_request WHERE change_request_id = ?", changeId);
        }
    }
}
