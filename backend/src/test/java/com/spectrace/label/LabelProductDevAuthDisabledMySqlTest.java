package com.spectrace.label;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=false")
class LabelProductDevAuthDisabledMySqlTest extends MySqlIntegrationTestSupport {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.spectrace.identity.application.IdentityService identities;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            POST|/api/labels/drafts|dev-external-label-officer|DEV_EXTERNAL
            POST|/api/labels/label_1106285_v1/review-submissions|dev-external-label-officer|DEV_EXTERNAL
            POST|/api/labels/label_1106285_v1/review-decisions|dev-external-qa-approver|DEV_EXTERNAL
            POST|/api/review-tasks/missing_task/publications|dev-external-publisher|DEV_EXTERNAL
            GET|/api/review-tasks/missing_task|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/labels/label_1106285_v1|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/labels/label_1106285_v1/declarations|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/v1/allergens?jurisdictionCode=US|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/v1/label-versions/label_1106285_v1/derived-allergens|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/v1/validation-runs/missing_run|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/v1/change-requests|dev-external-change-manager|DEV_EXTERNAL
            GET|/api/v1/change-requests/missing_change|dev-external-change-manager|DEV_EXTERNAL
            GET|/api/v1/impact-analyses/missing_analysis|dev-external-change-manager|DEV_EXTERNAL
            POST|/api/labels/drafts|dev-external-label-officer|dev_external
            POST|/api/v1/label-versions/label_1106285_v1/validation-runs|dev-external-label-officer|DEV_EXTERNAL
            GET|/api/labels/label_1106285_v1|dev-external-label-officer|DÉV_EXTERNAL
            """)
    void mappedDevelopmentHeadersCannotReadOrWriteWhenOptedOut(String method, String path,
                                                             String subject, String provider) throws Exception {
        var before = businessRows();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Auth-Provider", provider).header("X-External-Subject", subject);
        if ("GET".equals(method)) request.GET();
        else {
            String body = path.endsWith("/drafts")
                    ? "{\"productId\":\"prod_usda_1106285\",\"jurisdictionCode\":\"US\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"}]}"
                    : path.endsWith("/review-decisions") ? "{\"decision\":\"APPROVE\"}"
                    : path.endsWith("/publications") ? "{\"labelVersionId\":\"label_1106285_v1\"}"
                    : path.endsWith("/validation-runs") ? "{\"ruleSetVersionId\":\"ruleset_us_falcpa_demo_v1\"}" : "{}";
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        if (provider.chars().anyMatch(value -> value > 127)) {
            // Preserve the exact Latin-1 HTTP header at the server boundary; HttpClient's
            // encoding of non-ASCII values can otherwise test a different mojibake identity.
            var alias = jdbc.queryForMap("""
                    SELECT auth_provider, COLLATION(auth_provider) AS provider_collation,
                           (auth_provider = ?) AS matches_provider
                    FROM user_account WHERE external_auth_subject = ? AND is_active = 'Y'
                    """, provider, subject);
            System.out.println("DISABLED_AUTH_DATABASE_ALIAS: " + alias);
            if (((Number) alias.get("matches_provider")).intValue() == 1) {
                assertEquals("user_label_officer", identities.authenticate(provider, subject).userId(),
                        "Actual active identity mapping is the reproduction precondition");
            }
            String raw;
            try (var socket = new java.net.Socket("localhost", port)) {
                socket.setSoTimeout(20000);
                String headers = "GET " + path + " HTTP/1.1\r\nHost: localhost:" + port
                        + "\r\nX-Auth-Provider: " + provider + "\r\nX-External-Subject: " + subject
                        + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
                raw = new String(socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            int status = Integer.parseInt(raw.split(" ", 3)[1]);
            System.out.println("DISABLED_AUTH_LATIN1_PROVIDER: " + provider + " status=" + status);
            assertEquals(401, status, "Exact Latin-1 provider header: " + raw);
            org.junit.jupiter.api.Assertions.assertTrue(raw.contains("AUTHENTICATION_REQUIRED"), raw);
        } else {
            try (var client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (path.startsWith("/api/v1/change-requests") || path.startsWith("/api/v1/impact-analyses")) {
                    assertEquals("user_change_manager", identities.authenticate(provider, subject).userId());
                    assertEquals(before, businessRows(), "Impact reads must leave all business state unchanged");
                    System.out.println("DISABLED_IMPACT_AUTH_HTTP: " + method + " " + path + " status=" + response.statusCode());
                }
                assertEquals(401, response.statusCode(), method + " " + path + ": " + response.body());
                assertEquals("AUTHENTICATION_REQUIRED", JSON.readTree(response.body()).get("code").stringValue());
            }
        }
        assertEquals(before, businessRows(), "Disabled identity must leave all label workflow and validation state unchanged");
    }

    private List<List<java.util.Map<String, Object>>> businessRows() {
        return List.of(
                jdbc.queryForList("SELECT * FROM product ORDER BY product_id"),
                jdbc.queryForList("SELECT * FROM formula_version ORDER BY formula_version_id"),
                jdbc.queryForList("SELECT * FROM label_version ORDER BY label_version_id"),
                jdbc.queryForList("SELECT * FROM label_allergen_declaration ORDER BY label_allergen_declaration_id"),
                jdbc.queryForList("SELECT * FROM review_task ORDER BY review_task_id"),
                jdbc.queryForList("SELECT * FROM validation_run ORDER BY validation_run_id"),
                jdbc.queryForList("SELECT * FROM validation_result ORDER BY validation_result_id"),
                jdbc.queryForList("SELECT * FROM approval_record ORDER BY approval_record_id"),
                jdbc.queryForList("SELECT * FROM publication_record ORDER BY publication_record_id"),
                jdbc.queryForList("SELECT * FROM audit_event ORDER BY audit_event_id"),
                jdbc.queryForList("SELECT * FROM change_request ORDER BY change_request_id"),
                jdbc.queryForList("SELECT * FROM impact_analysis_run ORDER BY impact_analysis_run_id"),
                jdbc.queryForList("SELECT * FROM impact_finding ORDER BY impact_finding_id"));
    }
}
