package com.spectrace.validation;

import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = PositiveGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO `role`(role_id, role_code, role_name) VALUES ('role_scrum33_api', 'SCRUM33_API', 'Validation API fixture')",
        "INSERT INTO permission(permission_id, permission_code, permission_name) "
                + "VALUES ('permission_scrum33_api', 'LABEL.VALIDATE', 'Validate labels')",
        "INSERT INTO user_role(user_id, role_id, assigned_at) "
                + "VALUES ('user_s2_m2_fixture', 'role_scrum33_api', UTC_TIMESTAMP())",
        "INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum33_api', 'permission_scrum33_api')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ValidationApiMySqlIntegrationTest {
    private static final String LABEL = "label_s2_m2_multi_v1";
    private static final String FORMULA = "formula_s2_m2_multi_v1";
    private static final String RULE_SET = PositiveGoldenFixtures.RULE_SET_VERSION_ID;
    private static final String ACTOR = "user_s2_m2_fixture";
    private static final String SUBJECT = "s2-m2-positive-fixture";
    private static final String PROVENANCE = "prov_s2_m2_positive_v1";

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace")
            .withUsername("spectrace_scrum33")
            .withPassword("spectrace_scrum33_password");

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

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditApplicationService audit;

    private MockMvc mvc;
    private AuditApplicationService auditTarget;

    @BeforeEach
    void resetFixture() {
        mvc = webAppContextSetup(context).build();
        auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM validation_result");
        jdbc.update("DELETE FROM validation_run");
        jdbc.update("UPDATE label_version SET lifecycle_status = 'DRAFT', is_current_published = 'N' "
                + "WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", FORMULA);
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'ACTIVE', jurisdiction_code = 'US', "
                + "effective_from = '2026-01-01', effective_to = NULL WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE rule_definition SET severity = 'INFO', is_active = 'Y' WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE spec_component SET match_status = 'MATCHED' "
                + "WHERE spec_component_id = 'component_s2_m2_soy_v1'");
        jdbc.update("UPDATE user_account SET is_active = 'Y' WHERE user_id = ?", ACTOR);
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum33_api'");
        jdbc.update("INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum33_api', 'permission_scrum33_api')");
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        for (var declaration : PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).labelSnapshot().declarations()) {
            jdbc.update("""
                    INSERT INTO label_allergen_declaration(
                      label_allergen_declaration_id, label_version_id, allergen_id,
                      declaration_type, declaration_source, display_text, data_provenance_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, "scrum33-api-" + declaration.allergenId(), LABEL, declaration.allergenId(),
                    declaration.declarationType(), declaration.declarationSource(), declaration.displayText(), PROVENANCE);
        }
    }

    @Test
    void exposesAllergensAndRoundTripsACommittedValidationRun() throws Exception {
        mvc.perform(get("/api/v1/allergens").param("jurisdictionCode", "US").headers(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].allergenCode").isString());

        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labelVersionId").value(LABEL))
                .andExpect(jsonPath("$.ruleSetVersionId").value(RULE_SET))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.ranAt").isString())
                .andExpect(jsonPath("$.results", hasSize(3)));

        String runId = jdbc.queryForObject("SELECT validation_run_id FROM validation_run", String.class);
        mvc.perform(get("/api/v1/validation-runs/{id}", runId).headers(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationRunId").value(runId))
                .andExpect(jsonPath("$.results", hasSize(3)));
        assertCount("validation_run", 1);
        assertCount("validation_result", 3);
        assertCount("audit_event", 1);
    }

    @Test
    void rejectsUnauthenticatedStaleAndInactiveInputsWithoutWrites() throws Exception {
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET
                                + "\",\"actorId\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEmpty();

        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertEmpty();

        jdbc.update("UPDATE label_version SET lifecycle_status = 'PUBLISHED' WHERE label_version_id = ?", LABEL);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LABEL_VERSION_NOT_CURRENT"));
        assertEmpty();

        jdbc.update("UPDATE label_version SET lifecycle_status = 'DRAFT' WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'DRAFT' WHERE rule_set_version_id = ?", RULE_SET);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_PRECONDITION_FAILED"));
        assertEmpty();
    }

    @Test
    void convertsUnexpectedAuditFailureToSafe500AndRollsBack() throws Exception {
        doThrow(new IllegalStateException("SQL table secret")).when(auditTarget)
                .recordValidationEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("SQL"))));
        assertEmpty();
    }

    @Test
    void returnsCreatedForBlockingValidationFailure() throws Exception {
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE rule_definition SET severity = 'ERROR' WHERE rule_set_version_id = ?", RULE_SET);

        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .headers(auth()).contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].passed").value(false))
                .andExpect(jsonPath("$.results[0].blocking").value(true));
        assertCount("validation_run", 1);
        assertCount("validation_result", 3);
        assertCount("audit_event", 1);
    }

    private org.springframework.http.HttpHeaders auth() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Auth-Provider", "DEV_EXTERNAL");
        headers.add("X-External-Subject", SUBJECT);
        return headers;
    }

    private void assertEmpty() {
        assertCount("validation_run", 0);
        assertCount("validation_result", 0);
        assertCount("audit_event", 0);
    }

    private void assertCount(String table, int expected) {
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(expected);
    }
}
