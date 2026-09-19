package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures.Fixture;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.rule.RuleEvaluatorRegistry;
import com.spectrace.validation.domain.RuleDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Testcontainers
@SpringBootTest(properties = "spring.flyway.target=2")
@Sql(
        scripts = PositiveGoldenFixtures.SQL_RESOURCE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class PositiveGoldenFixtureMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("spectrace")
                    .withUsername("spectrace_fixture")
                    .withPassword("spectrace_fixture_password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LabelSnapshotPort labelSnapshots;

    @Autowired
    private FormulaCompositionPort formulas;

    @Autowired
    private AllergenFactsPort allergenFacts;

    @Autowired
    private RuleSetVersionRepository ruleSets;

    @Autowired
    private RuleEvaluatorRegistry evaluators;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    static Stream<Fixture> positiveFixtures() {
        return PositiveGoldenFixtures.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveFixtures")
    void actualOrchestratorMatchesPositiveGoldenFindingsAndEvaluatesEveryRule(Fixture fixture) {
        var integration = mock(ValidationIntegration.class);
        when(integration.requireActor(ValidationOrchestrator.VALIDATE_PERMISSION)).thenReturn("fixture-actor");
        var orchestrator = new ValidationOrchestrator(labelSnapshots, formulas, allergenFacts,
                ruleSets, evaluators, integration,
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));

        var evaluation = orchestrator.orchestrate(fixture.labelVersionId(), fixture.ruleSetVersionId());

        assertThat(evaluation.status()).isEqualTo(fixture.expectedResult());
        assertThat(evaluation.findings()).filteredOn(finding -> !finding.resultCode().endsWith("_NOT_DERIVED"))
                .containsExactlyInAnyOrderElementsOf(fixture.findings());
        assertThat(evaluation.findings()).extracting(finding -> finding.ruleDefinitionId())
                .containsExactlyInAnyOrderElementsOf(evaluation.ruleSet().ruleDefinitions().stream()
                        .filter(RuleDefinition::active).map(RuleDefinition::ruleDefinitionId).toList());
        assertThat(evaluation.findings()).allSatisfy(finding -> {
            assertThat(finding.passed()).isTrue();
            assertThat(finding.blocking()).isFalse();
        });
        verify(integration).requireActor(ValidationOrchestrator.VALIDATE_PERMISSION);
        verifyNoMoreInteractions(integration);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isZero();
    }

    @Test
    void sqlFixtureLoadsWithoutV3SeedAndOwnerPortsMatchGoldenTruth() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                String.class)).containsExactly("1", "2");

        for (Fixture fixture : PositiveGoldenFixtures.ALL) {
            assertThat(formulas.findById(fixture.formulaVersionId()))
                    .contains(fixture.formulaSnapshot());
            assertThat(labelSnapshots.findById(fixture.labelVersionId()))
                    .contains(fixture.labelSnapshot());
            assertThat(allergenFacts.derive(
                    fixture.formulaSnapshot(), fixture.ruleSetVersionId(), fixture.jurisdictionCode()))
                    .isEqualTo(fixture.expectedDerivation());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_provenance WHERE provenance_id = ?",
                Integer.class, "prov_s2_m2_positive_v1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM label_version WHERE label_version_id LIKE 'label_s2_m2_%_v1'",
                Integer.class)).isEqualTo(4);
    }
}
