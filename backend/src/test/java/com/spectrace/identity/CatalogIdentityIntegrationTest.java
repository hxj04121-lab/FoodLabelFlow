package com.spectrace.identity;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogIdentityIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminCanCreateSupplierAndAuditRecordsAuthenticatedActor()
            throws Exception {

        String code = "M4-HTTP-" + UUID.randomUUID();
        String base = "http://localhost:"
                + environment.getProperty("local.server.port");

        String body = """
                {
                  "code": "%s",
                  "name": "M4 HTTP Integration Supplier",
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

            assertEquals(201, response.statusCode());
        }

        String supplierId = jdbcTemplate.queryForObject(
                "SELECT supplier_id FROM supplier WHERE supplier_code = ?",
                String.class,
                code
        );

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_event
                WHERE entity_id = ?
                  AND event_type = 'SUPPLIER_CREATED'
                  AND actor_user_id = 'user_admin'
                """,
                Integer.class,
                supplierId
        );

        assertEquals(1, auditCount);
    }

    @Test
    void approverWithoutDataMaintainGets403AndNoSupplierIsCreated()
            throws Exception {

        String code = "M4-DENIED-" + UUID.randomUUID();
        String base = "http://localhost:"
                + environment.getProperty("local.server.port");

        String body = """
                {
                  "code": "%s",
                  "name": "M4 Unauthorized Supplier",
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
                            "dev-external-qa-approver"
                    )
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(403, response.statusCode());
            assertApiErrorContract(
                    response.body(),
                    "AUTHORIZATION_DENIED"
            );
        }

        Integer supplierCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier WHERE supplier_code = ?",
                Integer.class,
                code
        );

        assertEquals(0, supplierCount);
    }

    @Test
    void missingIdentityReturns401WithApiErrorContract()
            throws Exception {

        String code = "M4-NO-IDENTITY-" + UUID.randomUUID();
        String base = "http://localhost:"
                + environment.getProperty("local.server.port");

        String body = """
                {
                  "code": "%s",
                  "name": "Missing Identity Supplier",
                  "provenanceId": "prov_project_seed"
                }
                """.formatted(code);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(base + "/api/catalog/suppliers"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(401, response.statusCode());
            assertApiErrorContract(
                    response.body(),
                    "AUTHORIZATION_DENIED"
            );
        }

        Integer supplierCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier WHERE supplier_code = ?",
                Integer.class,
                code
        );

        assertEquals(0, supplierCount);
    }

    @Test
    void unknownExternalIdentityReturns401WithApiErrorContract()
            throws Exception {

        String code = "M4-UNKNOWN-" + UUID.randomUUID();
        String base = "http://localhost:"
                + environment.getProperty("local.server.port");

        String body = """
                {
                  "code": "%s",
                  "name": "Unknown Identity Supplier",
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
                            "dev-external-does-not-exist"
                    )
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(401, response.statusCode());
            assertApiErrorContract(
                    response.body(),
                    "AUTHORIZATION_DENIED"
            );
        }

        Integer supplierCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier WHERE supplier_code = ?",
                Integer.class,
                code
        );

        assertEquals(0, supplierCount);
    }

    private void assertApiErrorContract(
            String body,
            String expectedCode
    ) {
        assertTrue(
                body.contains("\"code\":\"" + expectedCode + "\"")
        );
        assertTrue(body.contains("\"message\":"));
        assertTrue(body.contains("\"traceId\":null"));
        assertTrue(body.contains("\"evidenceId\":null"));
    }
}