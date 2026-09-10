package com.spectrace.identity;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=false"
)
class DevExternalAuthDisabledIntegrationTest
        extends MySqlIntegrationTestSupport {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void devExternalHeadersCannotAuthorizeCatalogWriteWhenDisabled()
            throws Exception {

        String code = "M4-DISABLED-" + UUID.randomUUID();
        String base = "http://localhost:"
                + environment.getProperty("local.server.port");

        String body = """
                {
                  "code": "%s",
                  "name": "Disabled Dev Auth Supplier",
                  "provenanceId": "prov_project_seed"
                }
                """.formatted(code);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(base + "/api/catalog/suppliers"))
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Provider", "DEV_EXTERNAL")
                    .header(
                            "X-External-Subject",
                            "dev-external-admin"
                    )
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(503, response.statusCode());
        }

        Integer supplierCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM supplier
                WHERE supplier_code = ?
                """,
                Integer.class,
                code
        );

        assertEquals(0, supplierCount);
    }
}