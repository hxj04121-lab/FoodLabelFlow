package com.spectrace.validation;

import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationFinding;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP and committed MySQL evidence, with no test-managed transaction. */
abstract class ValidationHttpTestSupport {
    static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ValidationRunRepository runs;
    @Autowired
    ValidationResultRepository results;

    @BeforeEach
    void clearOutputs() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM validation_result");
        jdbc.update("DELETE FROM validation_run");
    }

    HttpResponse<String> request(String method, String path, String body, String subject) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (subject != null) {
            request.header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    HttpResponse<String> validate(String label, String ruleSet, String subject) throws Exception {
        return request("POST", "/api/v1/label-versions/" + label + "/validation-runs",
                JSON.writeValueAsString(Map.of("ruleSetVersionId", ruleSet)), subject);
    }

    JsonNode assertCommittedResponse(HttpResponse<String> response, String actor, String provenance) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        JsonNode body = JSON.readTree(response.body());
        String runId = body.get("validationRunId").stringValue();
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/validation-runs/" + runId);
        var saved = runs.findById(runId).orElseThrow();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "validationRunId", "labelVersionId", "ruleSetVersionId", "status", "ranAt", "results");
        assertThat(body.get("labelVersionId").stringValue()).isEqualTo(saved.labelVersionId());
        assertThat(body.get("ruleSetVersionId").stringValue()).isEqualTo(saved.ruleSetVersionId());
        assertThat(body.get("status").stringValue()).isEqualTo(saved.status().name());
        assertThat(body.get("ranAt").isString()).isTrue();
        assertThat(body.get("ranAt").stringValue()).endsWith("Z");
        assertThat(Instant.parse(body.get("ranAt").stringValue())).isEqualTo(saved.ranAt());
        assertThat(saved.ranByUserId()).isEqualTo(actor);
        assertThat(saved.dataProvenanceId()).isEqualTo(provenance);
        assertThat(body.get("results").isArray()).isTrue();
        for (JsonNode result : body.get("results")) {
            assertThat(fieldNames(result)).containsExactlyInAnyOrder(
                    "ruleDefinitionId", "resultCode", "severity", "passed", "blocking", "message");
            assertThat(result.get("passed").isBoolean()).isTrue();
            assertThat(result.get("blocking").isBoolean()).isTrue();
            assertThat(result.get("message").stringValue()).isNotBlank();
        }
        var persistedFindings = results.findByRunId(runId).stream().map(result -> new ValidationFinding(
                result.ruleDefinitionId(), result.resultCode(), result.severity(), result.passed(),
                result.blocking(), result.message())).toList();
        assertThat(findings(body)).containsExactlyElementsOf(persistedFindings);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class))
                .isEqualTo(persistedFindings.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT entity_id, actor_user_id, data_provenance_id,
                       event_payload->>'$.ruleSetVersionId' AS rule_set_id,
                       event_payload->>'$.validationRunId' AS run_id
                FROM audit_event WHERE correlation_id = ? AND event_type = 'LABEL_VALIDATION'
                """, runId))
                .containsEntry("entity_id", saved.labelVersionId())
                .containsEntry("actor_user_id", actor)
                .containsEntry("data_provenance_id", provenance)
                .containsEntry("rule_set_id", saved.ruleSetVersionId())
                .containsEntry("run_id", runId);
        return body;
    }

    static List<ValidationFinding> findings(JsonNode body) {
        return List.of(JSON.treeToValue(body.get("results"), ValidationFinding[].class));
    }

    static List<String> fieldNames(JsonNode body) {
        return body.properties().stream().map(Map.Entry::getKey).toList();
    }

    static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        JsonNode error = JSON.readTree(response.body());
        assertThat(fieldNames(error)).containsExactlyInAnyOrder("code", "message", "traceId", "evidenceId");
        assertThat(error.get("code").stringValue()).isEqualTo(code);
        assertThat(error.get("message").stringValue()).isNotBlank();
        assertThat(error.get("traceId").isNull()).isTrue();
        assertThat(error.get("evidenceId").isNull()).isTrue();
    }

    void assertNoOutputs() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
    }
}
