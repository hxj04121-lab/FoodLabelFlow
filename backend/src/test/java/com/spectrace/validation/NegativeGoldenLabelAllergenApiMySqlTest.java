package com.spectrace.validation;

import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.NegativeGoldenFixtures.Fixture;
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
import tools.jackson.databind.node.ObjectNode;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = NegativeGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class NegativeGoldenLabelAllergenApiMySqlTest extends ValidationHttpTestSupport {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("negative_facts").withPassword("negative_facts_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    static Stream<Fixture> fixtures() {
        return NegativeGoldenFixtures.ALL.stream();
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void displaysPinnedFactsIncludingRetiredRulesAndUnresolvedComponents(Fixture fixture) throws Exception {
        String labelId = fixture.labelSnapshot().labelVersionId();
        var response = request("GET", LabelAllergenApiMySqlTest.path(labelId), null, "s2-m2-negative-fixture");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        ObjectNode expected = JSON.valueToTree(fixture.expectedDerivation());
        expected.put("labelVersionId", labelId);
        assertThat(JSON.readTree(response.body())).isEqualTo(expected);
        assertNoOutputs();
    }
}
