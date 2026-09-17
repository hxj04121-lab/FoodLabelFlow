package com.spectrace;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true"
)
class FormulaLifecycleEndToEndTest extends MySqlIntegrationTestSupport {

    private static final String PRODUCT_ID = "prod_usda_1106285";
    private static final Pattern FORMULA_ID = Pattern.compile("\\\"formula_version_id\\\":\\\"([^\\\"]+)\\\"");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String originalFormulaId;
    private String newFormulaId;

    @AfterEach
    void restoreSharedFixtureAfterCommittedHttpRequests() {
        if (newFormulaId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbcTemplate.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?",
                    newFormulaId);
            jdbcTemplate.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?",
                    originalFormulaId);
            jdbcTemplate.update("UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                    originalFormulaId, PRODUCT_ID);
            jdbcTemplate.update("DELETE FROM audit_event WHERE entity_id = ? "
                    + "AND event_type IN ('FORMULA_CREATED', 'FORMULA_RELEASED')", newFormulaId);
            jdbcTemplate.update("DELETE FROM formula_item WHERE formula_version_id = ?", newFormulaId);
            jdbcTemplate.update("DELETE FROM formula_version WHERE formula_version_id = ?", newFormulaId);
        });
    }

    @Test
    void createsReleasesAndReadsImmutableFormulaHistoryThroughHttpAndMySql() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        originalFormulaId = jdbcTemplate.queryForObject(
                "SELECT current_formula_version_id FROM product WHERE product_id = ?",
                String.class,
                PRODUCT_ID
        );
        Integer originalItemCount = itemCount(originalFormulaId);

        HttpResponse<String> created = send(client, "POST", "/api/catalog/formulas", """
                {
                  "productId":"prod_usda_1106285",
                  "provenanceId":"prov_project_seed",
                  "items":[{
                    "materialId":"mat_chocolate_base",
                    "specificationId":"spec_chocolate_v1",
                    "quantity":12.5,
                    "unit":"kg"
                  }]
                }
                """);
        assertThat(created.statusCode()).isEqualTo(201);
        newFormulaId = formulaId(created.body());

        HttpResponse<String> released = send(client, "POST", "/api/catalog/formulas/" + newFormulaId + "/release", """
                {"expectedCurrentFormulaId":"%s"}
                """.formatted(originalFormulaId));
        assertThat(released.statusCode()).isEqualTo(200);
        assertThat(released.body()).contains("\"lifecycle_status\":\"RELEASED\"");

        HttpResponse<String> history = send(client, "GET", "/api/catalog/products/" + PRODUCT_ID + "/formulas", null);
        HttpResponse<String> trace = send(client, "GET", "/api/catalog/formulas/" + newFormulaId + "/trace", null);

        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains(originalFormulaId, newFormulaId);
        assertThat(trace.statusCode()).isEqualTo(200);
        assertThat(trace.body()).contains("mat_chocolate_base", "spec_chocolate_v1", "prov_project_seed");
        assertThat(itemCount(originalFormulaId)).isEqualTo(originalItemCount);
        assertThat(itemCount(newFormulaId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_formula_version_id FROM product WHERE product_id = ?",
                String.class,
                PRODUCT_ID
        )).isEqualTo(newFormulaId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE entity_id = ? AND event_type IN ('FORMULA_CREATED', 'FORMULA_RELEASED')",
                Integer.class,
                newFormulaId
        )).isEqualTo(2);
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", "dev-external-admin");
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Integer itemCount(String formulaId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formula_item WHERE formula_version_id = ?",
                Integer.class,
                formulaId
        );
    }

    private static String formulaId(String body) {
        Matcher matcher = FORMULA_ID.matcher(body);
        assertThat(matcher.find()).as("created formula response contains formula_version_id").isTrue();
        return matcher.group(1);
    }
}
