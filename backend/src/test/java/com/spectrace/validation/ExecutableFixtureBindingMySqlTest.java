package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day-5 binding gate: execute both M2 fixture families through owner ports and
 * the canonical MySQL Testcontainers runtime without invoking a validation seed
 * path.
 */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.target=2")
@Sql(
        scripts = {
                PositiveGoldenFixtures.SQL_RESOURCE,
                NegativeGoldenFixtures.SQL_RESOURCE
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class ExecutableFixtureBindingMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace_day5")
            .withUsername("spectrace_day5")
            .withPassword("spectrace_day5_password");

    @DynamicPropertySource
    static void day5DatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

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

    @Test
    void ownerPortsExecutePositiveAndNegativeFixturesWithoutFallback() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                String.class)).containsExactly("1", "2");

        for (var fixture : PositiveGoldenFixtures.ALL) {
            var formula = formulas.findById(fixture.formulaVersionId()).orElseThrow();
            var label = labelSnapshots.findById(fixture.labelVersionId()).orElseThrow();

            assertThat(formula).isEqualTo(fixture.formulaSnapshot());
            assertThat(label).isEqualTo(fixture.labelSnapshot());
            assertThat(ruleSets.findActiveById(fixture.ruleSetVersionId())).isPresent();
            assertThat(allergenFacts.derive(
                    formula, fixture.ruleSetVersionId(), fixture.jurisdictionCode()))
                    .isEqualTo(fixture.expectedDerivation());
            assertThat(fixture.expectedResult()).isEqualTo(ValidationStatus.PASSED);
        }

        for (var fixture : NegativeGoldenFixtures.ALL) {
            var formula = formulas.findById(fixture.formulaSnapshot().formulaVersionId()).orElseThrow();
            var label = labelSnapshots.findById(fixture.labelSnapshot().labelVersionId()).orElseThrow();

            assertThat(formula).isEqualTo(fixture.formulaSnapshot());
            assertThat(label).isEqualTo(fixture.labelSnapshot());
            assertThat(allergenFacts.derive(
                    formula, label.ruleSetVersionId(), label.jurisdictionCode()))
                    .isEqualTo(fixture.expectedDerivation());
            assertThat(fixture.expectedOutcome().passed()).isFalse();
            assertThat(fixture.expectedOutcome().blocking()).isTrue();
            if (fixture.expectedOutcome().completedRunStatus() != null) {
                assertThat(fixture.expectedOutcome().completedRunStatus())
                        .isEqualTo(ValidationStatus.FAILED);
                assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(201);
                assertThat(fixture.expectedOutcome().apiError()).isNull();
            } else {
                assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(422);
                assertThat(fixture.expectedOutcome().apiError()).isNotNull()
                        .extracting(error -> error.code(), error -> error.message(),
                                error -> error.traceId(), error -> error.evidenceId())
                        .containsExactly(
                                fixture.expectedOutcome().code(),
                                "The current label has no active rule-set available for evaluation.",
                                null, null);
            }

            if (fixture.activeRuleSetExpected()) {
                assertThat(ruleSets.findActiveById(label.ruleSetVersionId())).isPresent();
            } else {
                assertThat(ruleSets.findById(label.ruleSetVersionId()))
                        .get()
                        .extracting(ruleSet -> ruleSet.lifecycleStatus())
                        .isEqualTo(RuleSetLifecycleStatus.RETIRED);
                assertThat(ruleSets.findActiveById(label.ruleSetVersionId())).isEmpty();
                assertThat(ruleSets.findActiveById(
                        NegativeGoldenFixtures.MISSING_DECLARATION_RULE_SET_VERSION_ID))
                        .as("the exact retired rule-set ID must not be replaced by an active alternative")
                        .isPresent();
            }
        }
    }

    @Test
    void missingFixtureAndNegativeLabelsCannotBeSatisfiedByValidationSeedRows() {
        assertThat(formulas.findById("formula_s2_m2_not_a_fixture")).isEmpty();
        assertThat(labelSnapshots.findById("label_s2_m2_not_a_fixture")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_run WHERE label_version_id LIKE 'label_s2_m2_%'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_result vr "
                        + "JOIN validation_run run ON run.validation_run_id = vr.validation_run_id "
                        + "WHERE run.label_version_id LIKE 'label_s2_m2_%'",
                Integer.class)).isZero();
    }

}
