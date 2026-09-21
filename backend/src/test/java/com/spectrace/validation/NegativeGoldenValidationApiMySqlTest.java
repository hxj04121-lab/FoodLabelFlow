package com.spectrace.validation;

import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.NegativeGoldenFixtures.Fixture;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Keep the original negative SQL isolated from positive fixtures and the V3 demonstration seed. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = NegativeGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO `role`(role_id, role_code, role_name) VALUES ('role_scrum45', 'SCRUM45', 'HTTP fixture')",
        "INSERT INTO permission(permission_id, permission_code, permission_name) "
                + "VALUES ('permission_scrum45', 'LABEL.VALIDATE', 'Validate labels')",
        "INSERT INTO user_role(user_id, role_id, assigned_at) "
                + "VALUES ('user_s2_m2_negative_fixture', 'role_scrum45', UTC_TIMESTAMP())",
        "INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum45', 'permission_scrum45')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class NegativeGoldenValidationApiMySqlTest extends ValidationHttpTestSupport {
    private static final String SUBJECT = "s2-m2-negative-fixture";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("negative_http").withPassword("negative_http_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    static Stream<Fixture> goldenFixtures() {
        return NegativeGoldenFixtures.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFixtures")
    void negativeGoldenOutcomesMatchHttpContractAndCommittedEvidence(Fixture fixture) throws Exception {
        var label = fixture.labelSnapshot();
        var response = validate(label.labelVersionId(), label.ruleSetVersionId(), SUBJECT);
        var outcome = fixture.expectedOutcome();
        if (outcome.completedRunStatus() == null) {
            assertError(response, outcome.httpStatus(), outcome.code());
            assertNoOutputs();
            return;
        }

        var body = assertCommittedResponse(response, "user_s2_m2_negative_fixture", NegativeGoldenFixtures.PROVENANCE_ID);
        assertThat(body.get("status").stringValue()).isEqualTo(outcome.completedRunStatus().name());
        assertThat(body.get("labelVersionId").stringValue()).isEqualTo(label.labelVersionId());
        assertThat(body.get("ruleSetVersionId").stringValue()).isEqualTo(label.ruleSetVersionId());
        var expectedFindings = new ArrayList<>(fixture.expectedFindings());
        fixture.expectedDerivation().unresolvedComponents().forEach(component -> expectedFindings.add(new ValidationFinding(
                null, "FORMULA_COMPONENT_UNRESOLVED", ValidationSeverity.ERROR, false, true,
                "Formula component " + component.specComponentId() + " has unresolved match status " + component.matchStatus())));
        assertThat(findings(body)).containsExactlyInAnyOrderElementsOf(expectedFindings);
        var read = request("GET", "/api/v1/validation-runs/" + body.get("validationRunId").stringValue(), null, SUBJECT);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(read.body())).isEqualTo(body);
    }
}
