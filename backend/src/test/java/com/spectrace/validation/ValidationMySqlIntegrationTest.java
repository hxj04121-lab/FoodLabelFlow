package com.spectrace.validation;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ValidationMySqlIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String ACTIVE_RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String DRAFT_RULE_SET = "ruleset_us_falcpa_demo_v2";
    private static final String CURRENT_LABEL = "label_1106285_v1";
    private static final String PRODUCT = "prod_usda_1106285";
    private static final String AUTH_PROVIDER = "DEV_EXTERNAL";
    private static final String AUTHORIZED_SUBJECT = "dev-external-label-officer";
    private static final String UNAUTHORIZED_SUBJECT = "dev-external-qa-approver";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<String> createdRunIds = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreControlledFixtures() {
        for (String runId : createdRunIds) {
            jdbcTemplate.update("DELETE FROM audit_event WHERE correlation_id = ?", runId);
            jdbcTemplate.update("DELETE FROM validation_result WHERE validation_run_id = ?", runId);
            jdbcTemplate.update("DELETE FROM validation_run WHERE validation_run_id = ?", runId);
        }
        jdbcTemplate.update(
                "UPDATE product SET current_published_label_version_id = ? WHERE product_id = ?",
                CURRENT_LABEL, PRODUCT);
        jdbcTemplate.update(
                "UPDATE rule_set_version SET lifecycle_status = 'DRAFT' WHERE rule_set_version_id = ?",
                DRAFT_RULE_SET);
    }

    @Test
    void activeRuleSetCompletesHttpToMySqlRunResultAndAuditFlow() throws Exception {
        HttpResponse<String> response = post(CURRENT_LABEL, ACTIVE_RULE_SET, AUTHORIZED_SUBJECT);

        assertThat(response.statusCode()).isEqualTo(201);
        String json = response.body();
        String runId = jsonField(json, "validationRunId");
        createdRunIds.add(runId);
        assertThat(jsonField(json, "labelVersionId")).isEqualTo(CURRENT_LABEL);
        assertThat(jsonField(json, "ruleSetVersionId")).isEqualTo(ACTIVE_RULE_SET);
        assertThat(count(json, "ruleDefinitionId")).isEqualTo(4);
        assertThat(jsonField(json, "ranAt")).endsWith("Z");

        var run = jdbcTemplate.queryForMap(
                "SELECT validation_run_id, rule_set_version_id, ran_by_user_id, ran_at, status " +
                        "FROM validation_run WHERE validation_run_id = ?", runId);
        assertThat(run).containsEntry("validation_run_id", runId)
                .containsEntry("rule_set_version_id", ACTIVE_RULE_SET)
                .containsEntry("ran_by_user_id", "user_label_officer");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_result WHERE validation_run_id = ?", Integer.class, runId))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ? AND actor_user_id = ? " +
                        "AND entity_id = ?", Integer.class, runId, "user_label_officer", CURRENT_LABEL))
                .isEqualTo(1);
    }

    @Test
    void draftRetiredAndMissingRuleSetsDoNotCreatePartialRows() throws Exception {
        assertRejectedRuleSet(DRAFT_RULE_SET);

        jdbcTemplate.update(
                "UPDATE rule_set_version SET lifecycle_status = 'RETIRED' WHERE rule_set_version_id = ?",
                DRAFT_RULE_SET);
        assertRejectedRuleSet(DRAFT_RULE_SET);
        assertRejectedRuleSet("rule-set-does-not-exist");
    }

    @Test
    void staleCurrentPointerReturnsConflictWithoutPersistenceSideEffect() throws Exception {
        jdbcTemplate.update(
                "UPDATE product SET current_published_label_version_id = NULL WHERE product_id = ?", PRODUCT);

        HttpResponse<String> response = post(CURRENT_LABEL, ACTIVE_RULE_SET, AUTHORIZED_SUBJECT);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(jsonField(response.body(), "code")).isEqualTo("LABEL_VERSION_NOT_CURRENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_run WHERE label_version_id = ?", Integer.class, CURRENT_LABEL))
                .isZero();
    }

    @Test
    void authorizationDistinguishesUnauthorizedAndUnknownActors() throws Exception {
        HttpResponse<String> unauthorized = post(CURRENT_LABEL, ACTIVE_RULE_SET, UNAUTHORIZED_SUBJECT);
        assertThat(unauthorized.statusCode()).isEqualTo(403);
        assertThat(jsonField(unauthorized.body(), "code")).isEqualTo("AUTHORIZATION_DENIED");

        HttpResponse<String> unknown = post(CURRENT_LABEL, ACTIVE_RULE_SET, "unknown-subject");
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(jsonField(unknown.body(), "code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void unknownRunUsesCurrentAuthorizationAndNotFoundContract() throws Exception {
        HttpResponse<String> response = get("does-not-exist", AUTHORIZED_SUBJECT);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jsonField(response.body(), "code")).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void flywayAndCanonicalContainerRemainVisibleToValidationSuite() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3");
    }

    private void assertRejectedRuleSet(String ruleSetVersionId) throws Exception {
        int before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_run", Integer.class);
        HttpResponse<String> response = post(CURRENT_LABEL, ruleSetVersionId, AUTHORIZED_SUBJECT);
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(jsonField(response.body(), "code")).isEqualTo("VALIDATION_PRECONDITION_FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class))
                .isEqualTo(before);
    }

    private HttpResponse<String> post(String labelVersionId, String ruleSetVersionId, String subject)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/label-versions/" + labelVersionId + "/validation-runs"))
                .header("Content-Type", "application/json")
                .header("X-Auth-Provider", AUTH_PROVIDER)
                .header("X-External-Subject", subject)
                .POST(HttpRequest.BodyPublishers.ofString("{\"ruleSetVersionId\":\"" + ruleSetVersionId + "\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String runId, String subject) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/validation-runs/" + runId))
                .header("X-Auth-Provider", AUTH_PROVIDER)
                .header("X-External-Subject", subject)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field " + field + " in " + json);
        }
        return matcher.group(1);
    }

    private static int count(String value, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}
