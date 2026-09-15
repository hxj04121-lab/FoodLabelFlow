package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures.Fixture;
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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
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
