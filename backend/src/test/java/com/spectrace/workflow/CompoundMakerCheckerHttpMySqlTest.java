package com.spectrace.workflow;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

/**
 * The only added identity is in this class's private Testcontainer. Business
 * inputs go through actual production HTTP APIs, including the evaluator.
 * Spies only observe real authorization/policy execution; they do not fabricate
 * permissions, states or results. The separate direct procedure check covers
 * MySQL's own guard and is not presented as the HTTP implementation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompoundMakerCheckerHttpMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PRODUCT = "prod_usda_1123666";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("spectrace_test")
            .withPassword("spectrace_test_password");
    static { MYSQL.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @AfterAll
    static void stopOnlyOwnedDatabase() { MYSQL.stop(); }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityService identities;
    @Autowired private LabelReviewService reviewService;
    @MockitoSpyBean private AuthorizationService authorization;
    @MockitoSpyBean private MakerCheckerPolicy policy;

    @Test
    @Timeout(180)
    void compoundCreatorIsDeniedByRealTransactionalPolicyAndDatabaseGuardThenIndependentQaCanPublish()
            throws Exception {
        S3CompoundMakerFixture.install(jdbc);
        installReleasedSpecificationInput();
        var fixedIdentity = S3CompoundMakerFixture.rows(jdbc, S3CompoundMakerFixture.IDENTITY_TABLES);
        var maker = identities.authenticate("DEV_EXTERNAL", S3CompoundMakerFixture.SUBJECT);
        assertThat(maker.userId()).isEqualTo(S3CompoundMakerFixture.USER_ID);
        assertThat(maker.permissions()).contains("LABEL.CREATE", "LABEL.VALIDATE",
                "LABEL.SUBMIT_REVIEW", "LABEL.APPROVE");
        assertThat(identities.authenticate("DEV_EXTERNAL", S3CompoundMakerFixture.CASE_ALIAS_SUBJECT))
                .isEqualTo(maker);
        assertThat(AopUtils.isAopProxy(reviewService)).as("real Spring transaction interceptor").isTrue();
        var policyTransactions = new CopyOnWriteArrayList<Map<String, Object>>();
        doAnswer(invocation -> {
            policyTransactions.add(Map.of("makerUserId", invocation.getArgument(0),
                    "checkerUserId", invocation.getArgument(1),
                    "transactionActive", TransactionSynchronizationManager.isActualTransactionActive(),
                    "connectionId", Objects.requireNonNull(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class))));
            return invocation.callRealMethod();
        }).when(policy).requireIndependentChecker(anyString(), anyString());

        var evidence = new LinkedHashMap<String, Object>();
        var attempts = new ArrayList<Map<String, Object>>();
        evidence.put("fixtureIdentity", maker);
        evidence.put("identityRowsAfterFixture", fixedIdentity);
        evidence.put("sqlScope", List.of("new isolated fixture user and its two existing-role links",
                "released specification input only", "direct existing procedure self-approval negative; no output seeded"));
        var installedProcedure = jdbc.queryForMap("SHOW CREATE PROCEDURE sp_record_label_decision");
        assertThat(installedProcedure.get("Create Procedure")).as("actual installed MySQL decision procedure")
                .isInstanceOf(String.class);
        evidence.put("installedDecisionProcedure", installedProcedure);
        evidence.put("procedureObservationEnvironment", jdbc.queryForMap("SELECT DATABASE() AS database_name,"
                + "VERSION() AS mysql_version,CONNECTION_ID() AS observer_connection_id"));
        var originalProduct = jdbc.queryForMap("SELECT * FROM product WHERE product_id=?", PRODUCT);
        String oldFormula = (String) originalProduct.get("current_formula_version_id");
        String oldLabel = (String) originalProduct.get("current_published_label_version_id");
        var oldItems = jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY 1", oldFormula);
        var oldDeclarations = jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=? ORDER BY 1", oldLabel);
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var profile = expectJson(request(client, "GET", "/api/identity/current", null,
                    S3CompoundMakerFixture.SUBJECT), 200);
            var aliasProfile = expectJson(request(client, "GET", "/api/identity/current", null,
                    S3CompoundMakerFixture.CASE_ALIAS_SUBJECT), 200);
            assertThat(aliasProfile).isEqualTo(profile);
            assertThat(profile.get("userId").stringValue()).isEqualTo(maker.userId());
            assertThat(strings(profile.get("permissions"))).contains("LABEL.CREATE", "LABEL.APPROVE",
                    "LABEL.VALIDATE", "LABEL.SUBMIT_REVIEW");
            evidence.put("profile", profile);
            evidence.put("caseAliasProfile", aliasProfile);
            var adoption = expectJson(request(client, "POST", "/api/catalog/products/" + PRODUCT + "/formula-adoptions",
                    JSON.writeValueAsString(Map.of("sourceFormulaVersionId", oldFormula,
                            "targetSpecificationVersionId", "spec_chocolate_v2")), "dev-external-admin"), 201);
            String formula = adoption.get("formula_version_id").stringValue();
            // This ingredient-spec analysis requires all affected products to
            // have actually adopted the released input before the run begins.
            var adoptions = new LinkedHashMap<String, Object>();
            adoptions.put(PRODUCT, adoption);
            var remaining = jdbc.queryForList("SELECT p.product_id,p.current_formula_version_id FROM product p "
                    + "WHERE EXISTS (SELECT 1 FROM formula_item fi WHERE fi.formula_version_id=p.current_formula_version_id "
                    + "AND fi.supplier_material_id='mat_chocolate_base' AND fi.specification_version_id='spec_chocolate_v1') "
                    + "ORDER BY p.product_id");
            assertThat(remaining).hasSize(39);
            for (var product : remaining) {
                String productId = (String) product.get("product_id");
                adoptions.put(productId, expectJson(request(client, "POST", "/api/catalog/products/" + productId
                        + "/formula-adoptions", JSON.writeValueAsString(Map.of("sourceFormulaVersionId",
                        product.get("current_formula_version_id"), "targetSpecificationVersionId", "spec_chocolate_v2")),
                        "dev-external-admin"), 201));
            }
            assertThat(adoptions).hasSize(40);
            evidence.put("realHttpAdoptions", adoptions);
            var change = expectJson(request(client, "POST", "/api/v1/change-requests", """
                    {"changeType":"INGREDIENT_SPEC","supplierMaterialId":"mat_chocolate_base",
                     "previousSpecificationVersionId":"spec_chocolate_v1","targetSpecificationVersionId":"spec_chocolate_v2",
                     "description":"Isolated compound maker checker acceptance"}
                    """, "dev-external-change-manager"), 201);
            String ruleBody = JSON.writeValueAsString(Map.of("ruleSetVersionId", RULE_SET));
            var analysis = expectJson(request(client, "POST", "/api/v1/change-requests/"
                    + change.get("changeRequestId").stringValue() + "/impact-analyses", ruleBody,
                    "dev-external-change-manager"), 201);
            assertThat(analysis.get("relevantProductCount").intValue()).isEqualTo(40);
            assertThat(analysis.get("noActionCount").intValue()).isEqualTo(20);
            assertThat(analysis.get("reviewRequiredCount").intValue()).isEqualTo(20);
            JsonNode finding = StreamSupport.stream(analysis.get("findings").spliterator(), false)
                    .filter(node -> PRODUCT.equals(node.get("productId").stringValue())).findFirst().orElseThrow();
            assertThat(finding.get("outcome").stringValue()).isEqualTo("REVIEW_REQUIRED");
            assertThat(finding.get("proposedFormulaVersionId").stringValue()).isEqualTo(formula);
            String taskId = finding.get("reviewTask").get("reviewTaskId").stringValue();
            var declared = new TreeSet<String>();
            oldDeclarations.forEach(row -> declared.add((String) row.get("allergen_id")));
            assertThat(declared).as("this known product retains its explicit historical MILK declaration")
                    .contains("all_milk");
            declared.add("all_soy");
            var declarations = declared.stream().map(allergen -> Map.of("allergenId", allergen,
                    "declarationType", "CONTAINS", "displayText", "Contains " + allergen.substring(4))).toList();
            var draft = expectJson(request(client, "POST", "/api/labels/drafts", JSON.writeValueAsString(Map.of(
                    "productId", PRODUCT, "jurisdictionCode", "US", "reviewTaskId", taskId,
                    "declarations", declarations)), S3CompoundMakerFixture.SUBJECT), 201);
            String labelId = draft.get("labelVersionId").stringValue();
            assertThat(draft.get("createdByUserId").stringValue()).isEqualTo(maker.userId());
            assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM label_version WHERE label_version_id=?",
                    String.class, labelId)).isEqualTo(maker.userId());
            var validated = expectJson(request(client, "POST", "/api/v1/label-versions/" + labelId
                    + "/validation-runs", ruleBody, S3CompoundMakerFixture.SUBJECT), 201);
            assertThat(validated.get("status").stringValue()).as("actual evaluator, never SQL PASSED")
                    .isEqualTo("PASSED");
            expectJson(request(client, "POST", "/api/labels/" + labelId + "/review-submissions", "{}",
                    S3CompoundMakerFixture.SUBJECT), 200);
            assertThat(jdbc.queryForMap("SELECT * FROM review_task WHERE review_task_id=?", taskId))
                    .containsEntry("status", "IN_REVIEW").containsEntry("draft_label_version_id", labelId)
                    .containsEntry("target_label_version_id", labelId);
            assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?",
                    String.class, labelId)).isEqualTo("PENDING_REVIEW");
            String decisionPath = "/api/labels/" + labelId + "/review-decisions";
            String decision = "{\"decision\":\"APPROVE\",\"comments\":\"Real compound creator self-check\"}";
            var before = S3CompoundMakerFixture.allRows(jdbc);
            for (String subject : List.of(S3CompoundMakerFixture.SUBJECT, S3CompoundMakerFixture.CASE_ALIAS_SUBJECT)) {
                clearInvocations(authorization, policy);
                var denied = expectJson(request(client, "POST", decisionPath, decision, subject), 403);
                assertThat(denied.get("code").stringValue()).isEqualTo("AUTHORIZATION_DENIED");
                assertThat(denied.get("message").stringValue()).isEqualTo(S3CompoundMakerFixture.POLICY_MESSAGE);
                var actualExecution = inOrder(authorization, policy);
                actualExecution.verify(authorization).requirePermission(maker, "LABEL.APPROVE");
                actualExecution.verify(policy).requireIndependentChecker(maker.userId(), maker.userId());
                var after = S3CompoundMakerFixture.allRows(jdbc);
                assertThat(after).as("every business and identity row unchanged after policy denial for %s", subject)
                        .isEqualTo(before);
                attempts.add(Map.of("headerSubject", subject, "httpStatus", 403, "response", denied,
                        "allRowsUnchanged", true));
            }
            assertThat(policyTransactions).hasSize(2).allSatisfy(observation -> assertThat(observation)
                    .containsEntry("transactionActive", true).containsEntry("makerUserId", maker.userId())
                    .containsEntry("checkerUserId", maker.userId()));
            evidence.put("httpSelfApprovalAttempts", attempts);
            evidence.put("realPolicyTransactionObservations", policyTransactions);
            evidence.put("rowsBeforeSelfApproval", before);
            evidence.put("rowsAfterHttpDenials", S3CompoundMakerFixture.allRows(jdbc));

            // Separate database layer: HTTP uses Java/JDBC, not this procedure.
            JsonNode approved;
            try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
                 var call = connection.prepareCall("{call sp_record_label_decision(?,?,?,?)}")) {
                call.setString(1, labelId);
                call.setString(2, maker.userId());
                call.setString(3, "APPROVE");
                call.setString(4, "Direct procedure compound-maker negative only");
                SQLException error = org.assertj.core.api.Assertions.catchThrowableOfType(call::execute, SQLException.class);
                assertThat((Throwable) error).isNotNull();
                assertThat(error.getSQLState()).isEqualTo("45000");
                assertThat(error.getMessage()).contains("Maker-checker violation: label creator cannot approve own label");
                assertThat(S3CompoundMakerFixture.allRows(jdbc)).as("direct procedure denial also changes no rows")
                        .isEqualTo(before);
                evidence.put("rowsAfterDirectDatabaseDenial", S3CompoundMakerFixture.allRows(jdbc));
                // Keep the CALL connection open while independent QA succeeds
                // through a separate real HTTP transaction. Report this observed
                // behavior and unchanged rows without inferring lock placement.
                approved = expectJson(request(client, "POST", decisionPath,
                        "{\"decision\":\"APPROVE\",\"comments\":\"Independent existing QA after real denials\"}",
                        "dev-external-qa-approver"), 200);
                evidence.put("directDatabaseSelfApproval", Map.of("sqlState", error.getSQLState(),
                        "message", error.getMessage(), "allRowsUnchanged", true,
                        "independentQaApprovedWhileProcedureConnectionOpen", true));
            }
            assertThat(approved.get("lifecycleStatus").stringValue()).isEqualTo("APPROVED");
            var published = expectJson(request(client, "POST", "/api/review-tasks/" + taskId + "/publications",
                    JSON.writeValueAsString(Map.of("labelVersionId", labelId)), "dev-external-publisher"), 200);
            assertThat(published.get("lifecycleStatus").stringValue()).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForList("SELECT decided_by_user_id FROM approval_record WHERE label_version_id=?",
                    String.class, labelId)).containsExactly("user_approver");
            assertThat(jdbc.queryForList("SELECT published_by_user_id FROM publication_record WHERE label_version_id=?",
                    String.class, labelId)).containsExactly("user_publisher");
            assertThat(jdbc.queryForObject("SELECT current_published_label_version_id FROM product WHERE product_id=?",
                    String.class, PRODUCT)).isEqualTo(labelId);
            assertThat(jdbc.queryForList("SELECT * FROM formula_item WHERE formula_version_id=? ORDER BY 1", oldFormula))
                    .isEqualTo(oldItems);
            assertThat(jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=? ORDER BY 1", oldLabel))
                    .isEqualTo(oldDeclarations);
            assertThat(S3CompoundMakerFixture.rows(jdbc, S3CompoundMakerFixture.IDENTITY_TABLES)).isEqualTo(fixedIdentity);
            evidence.put("productId", PRODUCT);
            evidence.put("labelId", labelId);
            evidence.put("taskId", taskId);
            evidence.put("realValidation", validated);
            evidence.put("independentApproval", approved);
            evidence.put("independentPublication", published);
            evidence.put("finalRows", S3CompoundMakerFixture.allRows(jdbc));
            System.out.println("COMPOUND_MAKER_ACCEPTANCE=" + JSON.writeValueAsString(Map.of(
                    "productId", PRODUCT, "labelId", labelId, "taskId", taskId,
                    "realPolicyHttpDenials", 2, "directDatabaseSelfDenials", 1,
                    "allRowsUnchangedAfterDenials", true, "independentQaAndPublicationPassed", true)));
            String directory = System.getProperty("s3.compound.evidence");
            if (directory != null) {
                Path output = Path.of(directory).toAbsolutePath().normalize();
                Files.createDirectories(output);
                Files.writeString(output.resolve("compound-maker-http-database-observations.json"),
                        JSON.writeValueAsString(evidence), StandardCharsets.UTF_8);
            }
        }
    }

    private void installReleasedSpecificationInput() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/s3-soy-spec-v2-adoption.sql"))) {
            String fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int boundary = fixture.indexOf("INSERT INTO formula_version");
            assertThat(boundary).isPositive();
            String input = fixture.substring(0, boundary);
            assertThat(input).doesNotContain("label_version", "validation_run", "review_task");
            new ResourceDatabasePopulator(new ByteArrayResource(input.getBytes(StandardCharsets.UTF_8)))
                    .execute(Objects.requireNonNull(jdbc.getDataSource()));
        }
    }

    private List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::stringValue).toList();
    }

    private JsonNode expectJson(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as("actual HTTP response %s", response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(HttpClient client, String method, String endpoint,
                                         String body, String subject) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
