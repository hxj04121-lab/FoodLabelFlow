package com.spectrace.validation;

import com.spectrace.allergen.infrastructure.JdbcAllergenFactsAdapter;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures.Fixture;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/** No test transaction, no preseeded validation output and no LABEL.VALIDATE grant. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = PositiveGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class LabelAllergenApiMySqlTest extends ValidationHttpTestSupport {
    private static final String SUBJECT = "s2-m2-positive-fixture";
    private static final String LABEL = "label_s2_m2_soy_v1";
    private static final String RULE_SET = PositiveGoldenFixtures.RULE_SET_VERSION_ID;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("facts_fixture").withPassword("facts_fixture_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @MockitoSpyBean
    private JdbcAllergenFactsAdapter allergens;

    @BeforeEach
    void restoreFixtureFlags() {
        jdbc.update("UPDATE user_account SET is_active = 'Y'");
        jdbc.update("UPDATE label_version SET lifecycle_status = 'DRAFT'");
        jdbc.update("UPDATE formula_version SET is_current_released = 'Y'");
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'ACTIVE', jurisdiction_code = 'US'");
    }

    @AfterEach
    void neverCreatesValidationOrAuditOutputs() {
        assertNoOutputs();
    }

    static Stream<Fixture> fixtures() {
        return PositiveGoldenFixtures.ALL.stream();
    }

    static String path(String label) {
        return "/api/v1/label-versions/" + label + "/derived-allergens";
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void returnsExactGoldenFactsAndEvidenceWithoutMutatingInputs(Fixture fixture) throws Exception {
        var before = inputRows();
        var response = request("GET", path(fixture.labelVersionId()), null, SUBJECT);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        ObjectNode expected = JSON.valueToTree(fixture.expectedDerivation());
        expected.put("labelVersionId", fixture.labelVersionId());
        assertThat(JSON.readTree(response.body())).isEqualTo(expected);
        assertThat(inputRows()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission", Integer.class)).isZero();
    }

    @Test
    void historicalLabelUsesPinnedRetiredRuleSetAndOldFormulaNotCurrentAlternative() throws Exception {
        jdbc.update("UPDATE label_version SET lifecycle_status = 'SUPERSEDED' WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = 'formula_s2_m2_soy_v1'");
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'RETIRED' WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("""
                INSERT INTO rule_set_version(rule_set_version_id, rule_set_code, version_number,
                  jurisdiction_code, lifecycle_status, effective_from, is_demo_only, description, data_provenance_id)
                VALUES ('ruleset_new_display', 'DISPLAY_ALTERNATIVE', '1', 'US', 'ACTIVE', '2026-01-01', 'Y',
                  'Display binding regression fixture', 'prov_s2_m2_positive_v1')
                """);
        jdbc.update("""
                INSERT INTO ingredient_allergen(ingredient_allergen_id, ingredient_id, allergen_id,
                  rule_set_version_id, evidence_rule, data_provenance_id)
                VALUES ('ia_new_display', 'ing_s2_m2_soy_lecithin', 'all_milk', 'ruleset_new_display',
                  'Alternative must not replace pinned SOY mapping', 'prov_s2_m2_positive_v1')
                """);
        try {
            var response = request("GET", path(LABEL), null, SUBJECT);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            ObjectNode expected = JSON.valueToTree(PositiveGoldenFixtures.byId(PositiveGoldenFixtures.SOY_ID).expectedDerivation());
            expected.put("labelVersionId", LABEL);
            assertThat(JSON.readTree(response.body())).isEqualTo(expected);
        } finally {
            jdbc.update("DELETE FROM ingredient_allergen WHERE ingredient_allergen_id = 'ia_new_display'");
            jdbc.update("DELETE FROM rule_set_version WHERE rule_set_version_id = 'ruleset_new_display'");
        }
    }

    @Test
    void completeMatchedFormulaWithoutMappingsReturnsGenuineEmptyFacts() throws Exception {
        jdbc.update("DELETE FROM ingredient_allergen WHERE ingredient_allergen_id = 'ia_s2_m2_soy_v1'");
        try {
            var response = request("GET", path(LABEL), null, SUBJECT);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode body = JSON.readTree(response.body());
            assertThat(body.get("facts").isArray()).isTrue();
            assertThat(body.get("facts")).isEmpty();
            assertThat(body.get("unresolvedComponents")).isEmpty();
        } finally {
            jdbc.update("""
                    INSERT INTO ingredient_allergen(ingredient_allergen_id, ingredient_id, allergen_id,
                      rule_set_version_id, evidence_rule, data_provenance_id)
                    VALUES ('ia_s2_m2_soy_v1', 'ing_s2_m2_soy_lecithin', 'all_soy', ?,
                      'fixture mapping: soy lecithin -> SOY', 'prov_s2_m2_positive_v1')
                    """, RULE_SET);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "unknown-subject"})
    void missingOrUnknownIdentityIsRejectedBeforeLabelLookup(String subject) throws Exception {
        assertError(request("GET", path("missing"), null, subject), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void inactiveIdentityIsRejected() throws Exception {
        jdbc.update("UPDATE user_account SET is_active = 'N'");
        assertError(request("GET", path(LABEL), null, SUBJECT), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void missingLabelReturnsNotFoundNotEmptyFacts() throws Exception {
        assertError(request("GET", path("missing"), null, SUBJECT), 404, "RESOURCE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ruleSetVersionId=latest", "formulaVersionId=other", "jurisdictionCode=EU"})
    void clientCannotOverrideTheLabelBinding(String query) throws Exception {
        assertError(request("GET", path(LABEL) + "?" + query, null, SUBJECT), 400, "INVALID_REQUEST");
    }

    @Test
    void jurisdictionMismatchIsPreconditionFailure() throws Exception {
        jdbc.update("UPDATE rule_set_version SET jurisdiction_code = 'EU' WHERE rule_set_version_id = ?", RULE_SET);
        assertError(request("GET", path(LABEL), null, SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
    }

    @Test
    void incompleteFormulaIsNotDisguisedAsNoAllergens() throws Exception {
        jdbc.update("DELETE FROM formula_item WHERE formula_item_id = 'item_s2_m2_soy_v1'");
        try {
            assertError(request("GET", path(LABEL), null, SUBJECT), 422, "VALIDATION_PRECONDITION_FAILED");
        } finally {
            jdbc.update("""
                    INSERT INTO formula_item(formula_item_id, formula_version_id, supplier_material_id,
                      specification_version_id, sequence_no)
                    VALUES ('item_s2_m2_soy_v1', 'formula_s2_m2_soy_v1', 'mat_s2_m2_soy', 'spec_s2_m2_soy_v1', 1)
                    """);
        }
    }

    @Test
    void adapterFailureIsSafeInternalErrorNotAnEmptyList() throws Exception {
        doThrow(new IllegalStateException("private database details"))
                .when(allergens).derive(any(), anyString(), anyString());
        var response = request("GET", path(LABEL), null, SUBJECT);
        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("private database", "Exception");
    }

    private Map<String, List<Map<String, Object>>> inputRows() {
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("label_version", "label_allergen_declaration", "formula_version", "formula_item",
                "spec_component", "rule_set_version", "rule_definition", "ingredient_allergen", "allergen")) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return rows;
    }
}
