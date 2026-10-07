package com.spectrace;

import com.spectrace.audit.application.AuditApplicationService;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Day 4 explicit adoption: committed HTTP requests against MySQL, with isolated fixtures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
class FormulaSpecificationAdoptionMySqlTest extends MySqlIntegrationTestSupport {
    private static final String TARGET_PROVENANCE = "prov_scenario_input";
    private static final String ADMIN = "dev-external-admin";
    private static final Pattern FORMULA_ID = Pattern.compile("\\\"formula_version_id\\\":\\\"([^\\\"]+)\\\"");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private AuditApplicationService audit;

    private String productId;
    private String sourceId;
    private String materialId;
    private String previousSpecId;
    private String targetSpecId;
    private String publishedLabelId;
    private HttpClient client;

    @BeforeEach
    void createIndependentReleasedSourceAndTargetSpecification() {
        String token = UUID.randomUUID().toString();
        productId = "scrum55-product-" + token;
        sourceId = "scrum55-formula-" + token;
        materialId = "scrum55-material-" + token;
        previousSpecId = "scrum55-spec-v1-" + token;
        targetSpecId = "scrum55-spec-v2-" + token;
        publishedLabelId = "scrum55-label-" + token;
        client = HttpClient.newHttpClient();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("""
                    INSERT INTO product(product_id,fdc_id,brand_owner,product_description,normalized_category,
                      source_ingredients_text,source_type,fixture_group,data_provenance_id)
                    VALUES (?,?,'SCRUM-55 fixture','Explicit specification adoption fixture','TEST',
                      'Chocolate, soy','PROJECT_SEEDED','NO_ACTION_BASELINE_SOY','prov_project_seed')
                    """, productId, -Math.abs(UUID.randomUUID().getMostSignificantBits()));
            jdbc.update("""
                    INSERT INTO supplier_material(supplier_material_id,supplier_id,ingredient_id,material_code,
                      material_name,material_description,data_provenance_id)
                    SELECT ?,supplier_id,ingredient_id,?,'SCRUM-55 chocolate',material_description,data_provenance_id
                    FROM supplier_material WHERE supplier_material_id='mat_chocolate_base'
                    """, materialId, "SCRUM55-" + token);
            jdbc.update("""
                    INSERT INTO ingredient_specification_version(specification_version_id,supplier_material_id,
                      version_number,lifecycle_status,effective_date,released_at,created_by_user_id,data_provenance_id)
                    VALUES (?, ?, 1, 'RELEASED', '2020-01-01', '2020-01-01 09:00:00','user_admin','prov_project_seed'),
                           (?, ?, 2, 'RELEASED', '2020-01-01', '2020-01-02 09:00:00','user_admin',?)
                    """, previousSpecId, materialId, targetSpecId, materialId, TARGET_PROVENANCE);
            jdbc.update("""
                    INSERT INTO spec_component(spec_component_id,specification_version_id,ingredient_id,
                      raw_phrase,match_rule,match_status,sequence_no)
                    VALUES (?,?,'ing_cocoa','Cocoa','SCRUM-55 source fixture','MATCHED',1),
                           (?,?,'ing_cocoa','Cocoa','SCRUM-55 target fixture','MATCHED',1)
                    """, "scrum55-old-component-" + token, previousSpecId,
                    "scrum55-new-component-" + token, targetSpecId);
            jdbc.update("""
                    INSERT INTO formula_version(formula_version_id,product_id,version_number,lifecycle_status,
                      is_current_released,created_by_user_id,released_by_user_id,released_at,data_provenance_id)
                    VALUES (?, ?, 1, 'RELEASED','Y','user_admin','user_admin','2026-01-01 09:00:00','prov_project_seed')
                    """, sourceId, productId);
            // Repeated material and non-contiguous order exercise an exact snapshot, including nullable quantities.
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id,formula_version_id,supplier_material_id,
                      specification_version_id,sequence_no,quantity_value,quantity_unit)
                    VALUES (?,?,?,?,1,1.2500,'kg'),
                           (?,?,'mat_soy_carrier','spec_soy_carrier_v1',4,NULL,NULL),
                           (?,?,?,?,9,2.5000,'g')
                    """, "scrum55-item1-" + token, sourceId, materialId, previousSpecId,
                    "scrum55-item2-" + token, sourceId,
                    "scrum55-item3-" + token, sourceId, materialId, previousSpecId);
            jdbc.update("""
                    INSERT INTO label_version(label_version_id,product_id,formula_version_id,rule_set_version_id,
                      jurisdiction_code,version_number,raw_ingredient_text,lifecycle_status,is_current_published,
                      created_by_user_id,created_at,data_provenance_id)
                    SELECT ?,?,?,rule_set_version_id,jurisdiction_code,1,raw_ingredient_text,'PUBLISHED','Y',
                      created_by_user_id,created_at,data_provenance_id
                    FROM label_version WHERE label_version_id='label_1106285_v1'
                    """, publishedLabelId, productId, sourceId);
            jdbc.update("""
                    INSERT INTO label_allergen_declaration(label_allergen_declaration_id,label_version_id,
                      allergen_id,declaration_type,declaration_source,display_text,data_provenance_id)
                    VALUES (?,?,'all_soy','CONTAINS','FORMULA_DERIVED','Contains: Soy','prov_project_seed')
                    """, "scrum55-declaration-" + token, publishedLabelId);
            jdbc.update("""
                    UPDATE product SET current_formula_version_id=?,current_published_label_version_id=? WHERE product_id=?
                    """, sourceId, publishedLabelId, productId);
        });
    }

    @AfterEach
    void removeOnlyThisTestsCommittedFixtures() {
        AuditApplicationService auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        reset(auditTarget);
        if (client != null) client.close();
        if (productId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("UPDATE product SET current_formula_version_id=NULL,current_published_label_version_id=NULL WHERE product_id=?", productId);
            jdbc.update("DELETE FROM audit_event WHERE entity_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=?)", productId);
            jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id=?", publishedLabelId);
            jdbc.update("DELETE FROM label_version WHERE product_id=?", productId);
            jdbc.update("DELETE FROM formula_item WHERE formula_version_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=?)", productId);
            jdbc.update("DELETE FROM formula_version WHERE product_id=?", productId);
            jdbc.update("DELETE FROM product WHERE product_id=?", productId);
            jdbc.update("DELETE FROM spec_component WHERE specification_version_id IN (?,?)", previousSpecId, targetSpecId);
            jdbc.update("DELETE FROM ingredient_specification_version WHERE supplier_material_id=?", materialId);
            jdbc.update("DELETE FROM supplier_material WHERE supplier_material_id=?", materialId);
        });
    }

    @Test
    void explicitAdoptionReleasesNextVersionAndPreservesHistoricalContentAndPublishedLabel() throws Exception {
        var oldFormula = formula(sourceId);
        var oldItems = items(sourceId);
        var oldLabel = jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", publishedLabelId);
        var oldDeclarations = jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=?", publishedLabelId);
        var oldSpecification = jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", previousSpecId);
        var targetSpecification = jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecId);

        var response = adopt(sourceId, targetSpecId, ADMIN);

        assertThat(response.statusCode()).isEqualTo(201);
        String newId = formulaId(response.body());
        assertThat(newId).isNotEqualTo(sourceId);
        assertThat(response.body()).contains("\"version_number\":2", "\"lifecycle_status\":\"RELEASED\"",
                "\"items\":", targetSpecId, "spec_soy_carrier_v1");
        var current = formula(newId);
        assertThat(current).containsEntry("version_number", 2).containsEntry("lifecycle_status", "RELEASED")
                .containsEntry("is_current_released", "Y").containsEntry("created_by_user_id", "user_admin")
                .containsEntry("released_by_user_id", "user_admin").containsEntry("data_provenance_id", TARGET_PROVENANCE);
        assertThat(current.get("released_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?", String.class, productId)).isEqualTo(newId);
        assertThat(jdbc.queryForObject("SELECT current_published_label_version_id FROM product WHERE product_id=?", String.class, productId)).isEqualTo(publishedLabelId);
        assertThat(formula(sourceId)).containsEntry("is_current_released", "N");
        assertThat(historicalContent(formula(sourceId))).isEqualTo(historicalContent(oldFormula));
        assertThat(items(sourceId)).isEqualTo(oldItems);
        assertThat(jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", previousSpecId)).isEqualTo(oldSpecification);
        assertThat(jdbc.queryForMap("SELECT * FROM ingredient_specification_version WHERE specification_version_id=?", targetSpecId)).isEqualTo(targetSpecification);
        assertThat(jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", publishedLabelId)).isEqualTo(oldLabel);
        assertThat(jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=?", publishedLabelId)).isEqualTo(oldDeclarations);

        List<Map<String, Object>> expectedItems = new ArrayList<>();
        for (var oldItem : oldItems) {
            var expected = itemContent(oldItem);
            if (materialId.equals(oldItem.get("supplier_material_id"))) expected.put("specification_version_id", targetSpecId);
            expectedItems.add(expected);
        }
        var newItems = items(newId);
        assertThat(newItems.stream().map(FormulaSpecificationAdoptionMySqlTest::itemContent).toList()).isEqualTo(expectedItems);
        assertThat(newItems.stream().map(item -> item.get("formula_item_id")).toList())
                .doesNotContainAnyElementsOf(oldItems.stream().map(item -> item.get("formula_item_id")).toList());
        assertThat(newItems).extracting(item -> item.get("sequence_no")).containsExactly(1, 4, 9);
        assertThat((BigDecimal) newItems.getFirst().get("quantity_value")).isEqualByComparingTo("1.2500");
        assertThat(newItems.get(1)).containsEntry("quantity_value", null).containsEntry("quantity_unit", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_version WHERE product_id=? AND is_current_released='Y'", Integer.class, productId)).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT event_type FROM audit_event WHERE entity_id=?", String.class, newId))
                .containsExactlyInAnyOrder("FORMULA_CREATED", "FORMULA_RELEASED", "FORMULA_SPECIFICATION_ADOPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=? AND actor_user_id='user_admin' AND data_provenance_id=?", Integer.class, newId, TARGET_PROVENANCE)).isEqualTo(3);
        var adoptionEvidence = jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.sourceFormulaVersionId')) AS sourceId,
                       JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.targetSpecificationVersionId')) AS targetId,
                       JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.newFormulaVersionId')) AS newId
                FROM audit_event WHERE entity_id=? AND event_type='FORMULA_SPECIFICATION_ADOPTED'
                """, newId);
        assertThat(adoptionEvidence).containsEntry("sourceId", sourceId).containsEntry("targetId", targetSpecId).containsEntry("newId", newId);
        var trace = send("GET", "/api/catalog/formulas/" + newId + "/trace", null, null);
        assertThat(trace.statusCode()).isEqualTo(200);
        assertThat(trace.body()).contains(targetSpecId, TARGET_PROVENANCE, "SCRUM-55 target fixture");
    }

    @Test
    void missingIdentityAndInsufficientPermissionCannotWrite() throws Exception {
        assertError(adopt(sourceId, targetSpecId, null), 401, "AUTHENTICATION_REQUIRED");
        assertNothingWritten();
        assertError(adopt(sourceId, targetSpecId, "dev-external-qa-approver"), 403, "AUTHORIZATION_DENIED");
        assertNothingWritten();
    }

    @Test
    void invalidBodyReferencesAndUnreleasedOrFutureTargetCannotLeavePartialVersions() throws Exception {
        assertError(send("POST", adoptionPath(), "{\"sourceFormulaVersionId\":\" \"}", ADMIN), 400, "INVALID_REQUEST");
        assertNothingWritten();
        assertError(adopt("missing-source", targetSpecId, ADMIN), 422, "INVALID_REFERENCE");
        assertNothingWritten();
        assertError(adopt(sourceId, "missing-target", ADMIN), 422, "INVALID_REFERENCE");
        assertNothingWritten();
        assertError(adopt("formula_1106963_v1", targetSpecId, ADMIN), 422, "SOURCE_FORMULA_PRODUCT_MISMATCH");
        assertNothingWritten();
        jdbc.update("UPDATE ingredient_specification_version SET lifecycle_status='DRAFT' WHERE specification_version_id=?", targetSpecId);
        assertError(adopt(sourceId, targetSpecId, ADMIN), 422, "SPECIFICATION_NOT_RELEASED");
        assertNothingWritten();
        jdbc.update("UPDATE ingredient_specification_version SET lifecycle_status='RELEASED',effective_date=DATE_ADD(UTC_DATE(),INTERVAL 2 YEAR) WHERE specification_version_id=?", targetSpecId);
        assertError(adopt(sourceId, targetSpecId, ADMIN), 422, "SPECIFICATION_NOT_EFFECTIVE");
        assertNothingWritten();
    }

    @Test
    void auditFailureAfterInsertionRollsBackNewVersionItemsCurrentPointerAndAllAuditEvents() throws Exception {
        var oldFormula = formula(sourceId);
        var oldItems = items(sourceId);
        AuditApplicationService target = AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("controlled adoption audit failure");
        }).when(target).recordSpecificationAdoptionEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        var response = adopt(sourceId, targetSpecId, ADMIN);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("controlled adoption audit failure");
        assertNothingWritten();
        assertThat(formula(sourceId)).isEqualTo(oldFormula);
        assertThat(items(sourceId)).isEqualTo(oldItems);
    }

    private HttpResponse<String> adopt(String source, String specification, String subject) throws Exception {
        return send("POST", adoptionPath(), """
                {"sourceFormulaVersionId":"%s","targetSpecificationVersionId":"%s"}
                """.formatted(source, specification), subject);
    }

    private String adoptionPath() { return "/api/catalog/products/" + productId + "/formula-adoptions"; }

    private HttpResponse<String> send(String method, String path, String body, String subject) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (subject != null) request.header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertNothingWritten() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_version WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_item WHERE formula_version_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=?)", Integer.class, productId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id IN (SELECT formula_version_id FROM formula_version WHERE product_id=?)", Integer.class, productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_formula_version_id FROM product WHERE product_id=?", String.class, productId)).isEqualTo(sourceId);
        assertThat(jdbc.queryForObject("SELECT current_published_label_version_id FROM product WHERE product_id=?", String.class, productId)).isEqualTo(publishedLabelId);
        assertThat(formula(sourceId)).containsEntry("lifecycle_status", "RELEASED").containsEntry("is_current_released", "Y");
    }

    private Map<String, Object> formula(String id) { return jdbc.queryForMap("SELECT * FROM formula_version WHERE formula_version_id=?", id); }
    private List<Map<String, Object>> items(String id) { return jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY sequence_no", id); }

    private static Map<String, Object> historicalContent(Map<String, Object> formula) {
        var content = new LinkedHashMap<>(formula);
        content.remove("is_current_released");
        content.remove("current_formula_product_id");
        return content;
    }

    private static Map<String, Object> itemContent(Map<String, Object> item) {
        var content = new LinkedHashMap<>(item);
        content.remove("formula_item_id");
        content.remove("formula_version_id");
        return content;
    }

    private static String formulaId(String body) {
        var matcher = FORMULA_ID.matcher(body);
        assertThat(matcher.find()).as("created formula response includes its ID").isTrue();
        return matcher.group(1);
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).contains("\"code\":\"" + code + "\"", "\"message\":", "\"traceId\":null", "\"evidenceId\":null");
    }
}
