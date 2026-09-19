package com.spectrace.validation;

import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.NegativeGoldenFixtures.Fixture;
import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exact negative SQL in its own database; no test transaction hides commit/rollback. */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = NegativeGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO `role`(role_id, role_code, role_name) VALUES ('role_scrum44', 'SCRUM44', 'Validation fixture')",
        "INSERT INTO permission(permission_id, permission_code, permission_name) "
                + "VALUES ('permission_scrum44', 'LABEL.VALIDATE', 'Validate labels')",
        "INSERT INTO user_role(user_id, role_id, assigned_at) "
                + "VALUES ('user_s2_m2_negative_fixture', 'role_scrum44', UTC_TIMESTAMP())",
        "INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum44', 'permission_scrum44')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class NegativeGoldenValidationPersistenceMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace")
            .withUsername("spectrace_negative_fixture")
            .withPassword("spectrace_negative_fixture_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private ValidationApplicationService service;
    @Autowired
    private ValidationRunRepository runs;
    @Autowired
    private ValidationResultRepository results;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOutputsAndAuthenticate() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM validation_result");
        jdbc.update("DELETE FROM validation_run");
        var request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Provider", "DEV_EXTERNAL");
        request.addHeader("X-External-Subject", "s2-m2-negative-fixture");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    static Stream<Fixture> fixtures() {
        return NegativeGoldenFixtures.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void persistsCompletedGoldenFailuresButNotPreconditionFailures(Fixture fixture) {
        var label = fixture.labelSnapshot();
        if (fixture.expectedOutcome().completedRunStatus() == null) {
            assertThatThrownBy(() -> service.validate(label.labelVersionId(), label.ruleSetVersionId()))
                    .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(fixture.expectedOutcome().httpStatus());
                        assertThat(failure.code()).isEqualTo(fixture.expectedOutcome().code());
                    });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
            return;
        }

        var run = service.validate(label.labelVersionId(), label.ruleSetVersionId());

        assertThat(runs.findById(run.validationRunId())).contains(run);
        assertThat(run.status()).isEqualTo(fixture.expectedOutcome().completedRunStatus());
        assertThat(run.labelVersionId()).isEqualTo(label.labelVersionId());
        assertThat(run.ruleSetVersionId()).isEqualTo(label.ruleSetVersionId());
        assertThat(run.ranByUserId()).isEqualTo("user_s2_m2_negative_fixture");
        assertThat(run.dataProvenanceId()).isEqualTo(NegativeGoldenFixtures.PROVENANCE_ID);
        var saved = results.findByRunId(run.validationRunId());
        assertThat(saved).allSatisfy(result -> assertThat(result.validationRunId()).isEqualTo(run.validationRunId()));
        assertThat(saved.stream().filter(result -> result.ruleDefinitionId() != null)
                .map(result -> new ValidationFinding(result.ruleDefinitionId(), result.resultCode(),
                        result.severity(), result.passed(), result.blocking(), result.message())).toList())
                .containsExactlyElementsOf(fixture.expectedFindings());
        assertThat(saved).filteredOn(result -> result.ruleDefinitionId() == null)
                .hasSize(fixture.expectedDerivation().unresolvedComponents().size())
                .allSatisfy(result -> {
                    assertThat(result.resultCode()).isEqualTo("FORMULA_COMPONENT_UNRESOLVED");
                    assertThat(result.severity()).isEqualTo(ValidationSeverity.ERROR);
                    assertThat(result.passed()).isFalse();
                    assertThat(result.blocking()).isTrue();
                }).extracting(result -> result.message())
                .containsExactlyInAnyOrderElementsOf(fixture.expectedDerivation().unresolvedComponents().stream()
                        .map(component -> "Formula component " + component.specComponentId()
                                + " has unresolved match status " + component.matchStatus()).toList());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT entity_id, actor_user_id, data_provenance_id,
                       event_payload->>'$.ruleSetVersionId' AS rule_set_id
                FROM audit_event WHERE correlation_id = ? AND event_type = 'LABEL_VALIDATION'
                """, run.validationRunId()))
                .containsEntry("entity_id", label.labelVersionId())
                .containsEntry("actor_user_id", run.ranByUserId())
                .containsEntry("data_provenance_id", run.dataProvenanceId())
                .containsEntry("rule_set_id", label.ruleSetVersionId());
    }
}
