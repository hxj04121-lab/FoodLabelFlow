package com.spectrace.validation;

import com.spectrace.allergen.infrastructure.JdbcAllergenFactsAdapter;
import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures.Fixture;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.infrastructure.JdbcValidationResultRepository;
import com.spectrace.validation.infrastructure.JdbcValidationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = PositiveGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO `role`(role_id, role_code, role_name) VALUES ('role_scrum45', 'SCRUM45', 'HTTP fixture')",
        "INSERT INTO permission(permission_id, permission_code, permission_name) "
                + "VALUES ('permission_scrum45', 'LABEL.VALIDATE', 'Validate labels')",
        "INSERT INTO user_role(user_id, role_id, assigned_at) "
                + "VALUES ('user_s2_m2_fixture', 'role_scrum45', UTC_TIMESTAMP())"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ValidationApiMySqlTest extends ValidationHttpTestSupport {
    private static final String LABEL = "label_s2_m2_multi_v1";
    private static final String FORMULA = "formula_s2_m2_multi_v1";
    private static final String RULE_SET = PositiveGoldenFixtures.RULE_SET_VERSION_ID;
    private static final String ACTOR = "user_s2_m2_fixture";
    private static final String SUBJECT = "s2-m2-positive-fixture";
    private static final String PROVENANCE = "prov_s2_m2_positive_v1";
    private static final String RUN_PATH = "/api/v1/label-versions/" + LABEL + "/validation-runs";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("http_fixture").withPassword("http_fixture_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @MockitoSpyBean
    private AuditApplicationService audit;
    @MockitoSpyBean
    private JdbcAllergenFactsAdapter allergens;
    @MockitoSpyBean
    private JdbcValidationRunRepository runStore;
    @MockitoSpyBean
    private JdbcValidationResultRepository resultStore;

    @BeforeEach
    void restoreInputsAndPermission() {
        jdbc.update("UPDATE user_account SET is_active = 'Y' WHERE user_id = ?", ACTOR);
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum45'");
        jdbc.update("INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum45', 'permission_scrum45')");
        jdbc.update("UPDATE label_version SET lifecycle_status = 'DRAFT' WHERE label_version_id = ?", LABEL);
        var goldenLabel = PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).labelSnapshot();
        jdbc.update("UPDATE label_version SET raw_ingredient_text = ? WHERE label_version_id = ?",
                goldenLabel.rawIngredientText(), LABEL);
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        for (var declaration : goldenLabel.declarations()) {
            jdbc.update("""
                    INSERT INTO label_allergen_declaration(label_allergen_declaration_id, label_version_id,
                      allergen_id, declaration_type, declaration_source, display_text, data_provenance_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, "http-" + declaration.allergenId(), LABEL, declaration.allergenId(), declaration.declarationType(),
                    declaration.declarationSource(), declaration.displayText(), PROVENANCE);
        }
        jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", FORMULA);
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'ACTIVE', jurisdiction_code = 'US', "
                + "effective_from = '2026-01-01', effective_to = NULL WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE rule_definition SET is_active = 'Y', target_allergen_id = 'all_milk', "
                + "rule_type = 'LABEL_DECLARATION_VALIDATION' "
                + "WHERE rule_definition_id = 'rule_s2_m2_milk_decl'");
        jdbc.update("UPDATE rule_definition SET is_active = 'Y' WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("DELETE FROM formula_item WHERE formula_version_id = ?", FORMULA);
        var items = PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).formulaSnapshot().items();
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id, formula_version_id, supplier_material_id,
                                             specification_version_id, sequence_no)
                    VALUES (?, ?, ?, ?, ?)
                    """, item.formulaItemId(), FORMULA, item.supplierMaterialId(), item.specificationVersionId(), index + 1);
        }
    }

    static Stream<Fixture> goldenFixtures() {
        return PositiveGoldenFixtures.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFixtures")
    void positiveGoldenRunsRoundTripThroughHttpAndCommittedStorage(Fixture fixture) throws Exception {
        var label = fixture.labelSnapshot();
        var body = assertCommittedResponse(validate(label.labelVersionId(), RULE_SET, SUBJECT), ACTOR, PROVENANCE);

        assertThat(body.get("status").stringValue()).isEqualTo("PASSED");
        assertThat(body.get("labelVersionId").stringValue()).isEqualTo(label.labelVersionId());
        var expected = new ArrayList<>(fixture.findings());
        var targets = Map.of("rule_s2_m2_milk_decl", "all_milk", "rule_s2_m2_soy_decl", "all_soy",
                "rule_s2_m2_wheat_decl", "all_wheat");
        // All three active rules execute, including explicit passes for non-derived allergens.
        for (var rule : PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).findings()) {
            if (fixture.findings().stream().noneMatch(finding -> finding.ruleDefinitionId().equals(rule.ruleDefinitionId()))) {
                expected.add(new ValidationFinding(rule.ruleDefinitionId(), rule.resultCode() + "_NOT_DERIVED",
                        rule.severity(), true, false,
                        "No " + targets.get(rule.ruleDefinitionId()) + " allergen was derived from the formula"));
            }
        }
        assertThat(findings(body)).containsExactlyInAnyOrderElementsOf(expected);
        var read = request("GET", "/api/v1/validation-runs/" + body.get("validationRunId").stringValue(), null, SUBJECT);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(body);
    }

    @Test
    void readUsesStoredEvidenceEvenAfterInputsAndPermissionChange() throws Exception {
        ObjectNode body = (ObjectNode) assertCommittedResponse(validate(LABEL, RULE_SET, SUBJECT), ACTOR, PROVENANCE);
        String runId = body.get("validationRunId").stringValue();
        jdbc.update("UPDATE label_version SET lifecycle_status = 'SUPERSEDED', raw_ingredient_text = 'Changed after validation' "
                + "WHERE label_version_id = ?", LABEL);
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE rule_definition SET is_active = 'N' WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", FORMULA);
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum45'");
        jdbc.update("UPDATE validation_run SET summary = 'Persisted evidence' WHERE validation_run_id = ?", runId);
        body.put("summary", "Persisted evidence");

        var read = request("GET", "/api/v1/validation-runs/" + runId, null, SUBJECT);

        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(body);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        // M4's existing authenticated read policy does not grant write permission.
        assertError(validate(LABEL, RULE_SET, SUBJECT), 403, "AUTHORIZATION_DENIED");
    }

    @Test
    void catalogueIsCanonicalSortedAndJurisdictionScoped() throws Exception {
        var response = request("GET", "/api/v1/allergens?jurisdictionCode=US", null, SUBJECT);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                [
                  {"allergenId":"all_milk","allergenCode":"MILK","displayName":"Milk","jurisdictionCode":"US"},
                  {"allergenId":"all_soy","allergenCode":"SOY","displayName":"Soy","jurisdictionCode":"US"},
                  {"allergenId":"all_wheat","allergenCode":"WHEAT","displayName":"Wheat","jurisdictionCode":"US"}
                ]
                """));
        var empty = request("GET", "/api/v1/allergens?jurisdictionCode=GB", null, SUBJECT);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(empty.body())).isEmpty();
        assertNoOutputs();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?jurisdictionCode=", "?jurisdictionCode=%20"})
    void rejectsMissingOrBlankJurisdiction(String query) throws Exception {
        assertError(request("GET", "/api/v1/allergens" + query, null, SUBJECT), 400, "INVALID_REQUEST");
        assertNoOutputs();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "null", "{}", "[]", "true", "42", "\"ruleset\"", "{",
            "{\"ruleSetVersionId\":null}", "{\"ruleSetVersionId\":\"\"}", "{\"ruleSetVersionId\":\"  \"}",
            "{\"ruleSetVersionId\":1}", "{\"ruleSetVersionId\":true}", "{\"ruleSetVersionId\":[]}",
            "{\"ruleSetVersionId\":{}}", "{\"ruleSetVersionId\":\"rules\",\"actorId\":\"user_admin\"}",
            "{\"ruleSetVersionId\":\"first\",\"ruleSetVersionId\":\"second\"}",
            "{\"ruleSetVersionId\":\"rules\"} {}"
    })
    void rejectsMalformedOrNonContractBodiesWithoutWriting(String body) throws Exception {
        assertError(request("POST", RUN_PATH, body, SUBJECT), 400, "INVALID_REQUEST");
        assertNoOutputs();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"unknown-subject", ""})
    void allEndpointsRejectMissingOrUnknownIdentity(String subject) throws Exception {
        assertError(validate(LABEL, RULE_SET, subject), 401, "AUTHENTICATION_REQUIRED");
        assertError(request("GET", "/api/v1/allergens?jurisdictionCode=US", null, subject), 401, "AUTHENTICATION_REQUIRED");
        assertError(request("GET", "/api/v1/validation-runs/missing", null, subject), 401, "AUTHENTICATION_REQUIRED");
        assertNoOutputs();
    }

    @Test
    void allEndpointsRejectInactiveIdentity() throws Exception {
        jdbc.update("UPDATE user_account SET is_active = 'N' WHERE user_id = ?", ACTOR);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 401, "AUTHENTICATION_REQUIRED");
        assertError(request("GET", "/api/v1/allergens?jurisdictionCode=US", null, SUBJECT), 401, "AUTHENTICATION_REQUIRED");
        assertError(request("GET", "/api/v1/validation-runs/missing", null, SUBJECT), 401, "AUTHENTICATION_REQUIRED");
        assertNoOutputs();
    }

    @Test
    void deniesWritesWithoutCanonicalPermissionBeforeLookingUpLabel() throws Exception {
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum45'");
        assertError(validate("missing-label", RULE_SET, SUBJECT), 403, "AUTHORIZATION_DENIED");
        assertThat(request("GET", "/api/v1/allergens?jurisdictionCode=US", null, SUBJECT).statusCode()).isEqualTo(200);
        assertNoOutputs();
    }

    @Test
    void missingLabelAndRunUseCanonicalNotFoundEnvelope() throws Exception {
        assertError(validate("missing-label", RULE_SET, SUBJECT), 404, "RESOURCE_NOT_FOUND");
        assertError(request("GET", "/api/v1/validation-runs/missing", null, SUBJECT), 404, "RESOURCE_NOT_FOUND");
        assertNoOutputs();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERSEDED", "REJECTED", "PUBLISHED"})
    void rejectsOldLabelBeforeIncompleteFormula(String lifecycle) throws Exception {
        jdbc.update("UPDATE label_version SET lifecycle_status = ? WHERE label_version_id = ?", lifecycle, LABEL);
        jdbc.update("DELETE FROM formula_item WHERE formula_version_id = ?", FORMULA);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 409, "LABEL_VERSION_NOT_CURRENT");
        assertNoOutputs();
    }

    @Test
    void rejectsStaleFormulaAndIncompleteCurrentFormulaDistinctly() throws Exception {
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", FORMULA);
        jdbc.update("DELETE FROM formula_item WHERE formula_version_id = ?", FORMULA);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 409, "LABEL_VERSION_NOT_CURRENT");
        assertNoOutputs();
        jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", FORMULA);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
        assertNoOutputs();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lifecycle_status = 'DRAFT'", "lifecycle_status = 'RETIRED'", "effective_from = '9999-01-01'",
            "effective_to = '2026-01-02'", "jurisdiction_code = 'EU'"
    })
    void rejectsUnusableExactRuleSet(String change) throws Exception {
        jdbc.update("UPDATE rule_set_version SET " + change + " WHERE rule_set_version_id = ?", RULE_SET);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
        assertNoOutputs();
    }

    @Test
    void doesNotSubstituteRuleSetOrAcceptEmptyActiveDefinitions() throws Exception {
        assertError(validate(LABEL, "different-rule-set", SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
        assertNoOutputs();
        jdbc.update("UPDATE rule_definition SET is_active = 'N' WHERE rule_set_version_id = ?", RULE_SET);
        assertError(validate(LABEL, RULE_SET, SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
        assertNoOutputs();
    }

    @Test
    void invalidEvaluatorConfigurationIsInternalNotBadClientInput() throws Exception {
        jdbc.update("UPDATE rule_definition SET target_allergen_id = NULL, rule_type = 'INGREDIENT_TO_ALLERGEN' "
                + "WHERE rule_definition_id = 'rule_s2_m2_milk_decl'");
        var response = validate(LABEL, RULE_SET, SUBJECT);
        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("rule_s2_m2_milk_decl", "targetAllergenId", "Exception");
        assertNoOutputs();
    }

    @Test
    void auditFailureAfterInsertRollsBackEverythingBeforeHttpResponse() throws Exception {
        AuditApplicationService target = AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("private database details: audit write failed");
        }).when(target).recordValidationEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        var response = validate(LABEL, RULE_SET, SUBJECT);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("private database", "audit write", "Exception");
        assertNoOutputs();
    }

    @Test
    void resultBatchFailureReturnsSafeErrorAndRollsBackPartialWrites() throws Exception {
        doAnswer(invocation -> {
            List<ValidationResult> values = invocation.getArgument(1);
            var writer = new JdbcValidationResultRepository(jdbc);
            writer.saveAll(invocation.getArgument(0), List.of(values.getFirst()));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isEqualTo(1);
            writer.saveAll(invocation.getArgument(0), values); // Real duplicate key after the first insert.
            return null;
        }).when(resultStore).saveAll(anyString(), anyList());

        var response = validate(LABEL, RULE_SET, SUBJECT);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("INSERT", "Duplicate", "Exception");
        assertNoOutputs();
    }

    @Test
    void committedCreationDoesNotRequireASecondDatabaseRead() throws Exception {
        doThrow(new IllegalStateException("private read failure")).when(runStore).findById(anyString());

        var response = validate(LABEL, RULE_SET, SUBJECT);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(findings(JSON.readTree(response.body())))
                .containsExactlyInAnyOrderElementsOf(PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).findings());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        // A separate read failure is a safe 500, not a false 404 or an empty results fallback.
        var read = request("GET", response.headers().firstValue("Location").orElseThrow(), null, SUBJECT);
        assertError(read, 500, "INTERNAL_ERROR");
        assertThat(read.body()).doesNotContain("private read", "Exception");
    }

    @Test
    void catalogueFailureIsNotDisguisedAsEmptyCatalogueOrBadInput() throws Exception {
        doThrow(new IllegalArgumentException("private adapter configuration"))
                .when(allergens).listAllergens("US");
        var response = request("GET", "/api/v1/allergens?jurisdictionCode=US", null, SUBJECT);
        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("private adapter", "Exception");
        assertNoOutputs();
    }
}
