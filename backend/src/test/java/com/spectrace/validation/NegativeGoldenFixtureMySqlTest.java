package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.NegativeGoldenFixtures.Fixture;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
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

@Testcontainers
@SpringBootTest(properties = "spring.flyway.target=2")
@Sql(
        scripts = NegativeGoldenFixtures.SQL_RESOURCE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class NegativeGoldenFixtureMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("spectrace")
                    .withUsername("spectrace_negative_fixture")
                    .withPassword("spectrace_negative_fixture_password");

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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void isolatedOwnerPortsPreserveEveryNegativeTruthWithoutFallback() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                String.class)).containsExactly("1", "2");

        for (Fixture fixture : NegativeGoldenFixtures.ALL) {
            var formula = formulas.findById(fixture.formulaSnapshot().formulaVersionId());
            var label = labelSnapshots.findById(fixture.labelSnapshot().labelVersionId());

            assertThat(formula).contains(fixture.formulaSnapshot());
            assertThat(label).contains(fixture.labelSnapshot());
            assertThat(allergenFacts.derive(
                    formula.orElseThrow(),
                    fixture.labelSnapshot().ruleSetVersionId(),
                    fixture.labelSnapshot().jurisdictionCode()))
                    .isEqualTo(fixture.expectedDerivation());

            if (fixture.activeRuleSetExpected()) {
                assertThat(ruleSets.findActiveById(fixture.labelSnapshot().ruleSetVersionId()))
                        .isPresent();
            } else {
                assertThat(ruleSets.findById(fixture.labelSnapshot().ruleSetVersionId()))
                        .get()
                        .extracting(ruleSet -> ruleSet.lifecycleStatus())
                        .isEqualTo(RuleSetLifecycleStatus.RETIRED);
                assertThat(ruleSets.findActiveById(fixture.labelSnapshot().ruleSetVersionId()))
                        .isEmpty();
                assertThat(ruleSets.findActiveById(
                        NegativeGoldenFixtures.MISSING_DECLARATION_RULE_SET_VERSION_ID))
                        .as("an available active alternative must not replace the exact requested ID")
                        .isPresent();
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM label_version WHERE label_version_id LIKE 'label_s2_m2_neg_%'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_run WHERE label_version_id LIKE 'label_s2_m2_neg_%'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM validation_result vr
                JOIN validation_run run ON run.validation_run_id = vr.validation_run_id
                WHERE run.label_version_id LIKE 'label_s2_m2_neg_%'
                """,
                Integer.class)).isZero();
    }
}
