package com.spectrace.label;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP, real owner adapters and the full Flyway seed; no mocked application beans. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LabelDeclarationsHttpMySqlTest {
    private static final String LABEL = "label_1106285_v1";
    private static final String PATH = "/api/labels/" + LABEL + "/declarations";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("declaration_test").withPassword("declaration_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seededDeclarationsMatchRealDatabaseContractAndM1Binding() throws Exception {
        var before = outputCounts();
        JsonNode body = successfulGet(PATH);
        assertThat(body).isEqualTo(JSON.readTree("""
                {
                  "labelVersionId": "label_1106285_v1",
                  "formulaVersionId": "formula_1106285_v1",
                  "ruleSetVersionId": "ruleset_us_falcpa_demo_v1",
                  "jurisdictionCode": "US",
                  "declarations": [
                    {"allergenId":"all_soy","declarationType":"CONTAINS",
                     "declarationSource":"FORMULA_DERIVED","displayText":"Contains: Soy"},
                    {"allergenId":"all_wheat","declarationType":"CONTAINS",
                     "declarationSource":"FORMULA_DERIVED","displayText":"Contains: Wheat"}
                  ]
                }
                """));
        JsonNode derived = successfulGet("/api/v1/label-versions/" + LABEL + "/derived-allergens");
        for (String field : List.of("labelVersionId", "formulaVersionId", "ruleSetVersionId", "jurisdictionCode")) {
            assertThat(body.get(field)).as(field).isEqualTo(derived.get(field));
        }
        assertThat(outputCounts()).isEqualTo(before);
    }

    @Test
    void historicalLabelKeepsItsDeclarationsWhenANewerEmptyDraftExists() throws Exception {
        var created = request("POST", "/api/labels/drafts",
                "{\"productId\":\"prod_usda_1106285\",\"jurisdictionCode\":\"US\"}", true);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String newId = JSON.readTree(created.body()).get("labelVersionId").stringValue();
        jdbc.update("UPDATE label_version SET lifecycle_status='SUPERSEDED', is_current_published='N' WHERE label_version_id=?", LABEL);
        try {
            JsonNode empty = successfulGet("/api/labels/" + newId + "/declarations");
            assertThat(empty.get("labelVersionId").stringValue()).isEqualTo(newId);
            assertThat(empty.get("jurisdictionCode").stringValue()).isEqualTo("US");
            assertThat(empty.get("declarations")).isEmpty();
            JsonNode historical = successfulGet(PATH);
            assertThat(historical.get("labelVersionId").stringValue()).isEqualTo(LABEL);
            assertThat(historical.get("declarations").size()).isEqualTo(2);
        } finally {
            jdbc.update("UPDATE label_version SET lifecycle_status='PUBLISHED', is_current_published='Y' WHERE label_version_id=?", LABEL);
            jdbc.update("DELETE FROM label_version WHERE label_version_id=?", newId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIGRATED_PUBLIC_LABEL", "FORMULA_DERIVED", "SYSTEM_PROPOSED", "USER_ENTERED"})
    void returnsEachDatabaseLegalSourceAndNullableDisplayText(String source) throws Exception {
        jdbc.update("UPDATE label_allergen_declaration SET declaration_source=?, display_text=NULL WHERE label_allergen_declaration_id='lad_1106285_v1_soy'", source);
        try {
            JsonNode declaration = successfulGet(PATH).get("declarations").get(0);
            assertThat(declaration.get("declarationSource").stringValue()).isEqualTo(source);
            assertThat(declaration.get("displayText").isNull()).isTrue();
        } finally {
            jdbc.update("UPDATE label_allergen_declaration SET declaration_source='FORMULA_DERIVED', display_text='Contains: Soy' WHERE label_allergen_declaration_id='lad_1106285_v1_soy'");
        }
    }

    @Test
    void missingIdentityAndMissingLabelUseRealHttpErrors() throws Exception {
        var anonymous = request("GET", PATH, null, false);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(anonymous.body()).get("code").stringValue()).isEqualTo("AUTHENTICATION_REQUIRED");
        var missing = request("GET", "/api/labels/missing/declarations", null, true);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(missing.body()).get("code").stringValue()).isEqualTo("LABEL_NOT_FOUND");
    }

    private JsonNode successfulGet(String path) throws Exception {
        var response = request("GET", path, null, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String body, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (authenticated) {
            request.header("X-Auth-Provider", "DEV_EXTERNAL")
                    .header("X-External-Subject", "dev-external-label-officer");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private Map<String, Integer> outputCounts() {
        return Map.of(
                "runs", jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class),
                "results", jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class),
                "approvals", jdbc.queryForObject("SELECT COUNT(*) FROM approval_record", Integer.class),
                "audit", jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
    }
}
