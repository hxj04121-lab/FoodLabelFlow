package com.spectrace.validation;

import com.spectrace.support.fixture.PositiveGoldenFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.target=2"
)
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
@Sql(
        scripts = PositiveGoldenFixtures.SQL_RESOURCE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class LabelDeclarationsApiMySqlTest
        extends ValidationHttpTestSupport {

    private static final String SUBJECT =
            "s2-m2-positive-fixture";

    private static final String LABEL =
            "label_s2_m2_soy_v1";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("spectrace")
                    .withUsername("declaration_fixture")
                    .withPassword(
                            "declaration_fixture_password"
                    );

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername
        );
        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword
        );
    }

    @Test
    void returnsSeededStructuredDeclarationsWithVersionBinding()
            throws Exception {

        var response = request(
                "GET",
                "/api/labels/"
                        + LABEL
                        + "/declarations",
                null,
                SUBJECT
        );

        assertThat(response.statusCode())
                .as(response.body())
                .isEqualTo(200);

        JsonNode body =
                JSON.readTree(response.body());

        assertThat(
                body.get("labelVersionId").asText()
        ).isEqualTo(
                "label_s2_m2_soy_v1"
        );

        assertThat(
                body.get("formulaVersionId").asText()
        ).isEqualTo(
                "formula_s2_m2_soy_v1"
        );

        assertThat(
                body.get("ruleSetVersionId").asText()
        ).isEqualTo(
                PositiveGoldenFixtures.RULE_SET_VERSION_ID
        );

        assertThat(
                body.get("jurisdictionCode").asText()
        ).isEqualTo("US");

        JsonNode declarations =
                body.get("declarations");

        assertThat(declarations.isArray()).isTrue();
        assertThat(declarations.size()).isEqualTo(1);

        JsonNode declaration =
                declarations.get(0);

        assertThat(
                declaration.get("allergenId").asText()
        ).isEqualTo("all_soy");

        assertThat(
                declaration.get("declarationType").asText()
        ).isEqualTo("CONTAINS");

        assertThat(
                declaration.get("declarationSource").asText()
        ).isEqualTo("FORMULA_DERIVED");

        assertThat(
                declaration.get("displayText").asText()
        ).isEqualTo("Contains: Soy");
    }
}