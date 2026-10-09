package com.spectrace.workflow;

import com.spectrace.workflow.infrastructure.JdbcReviewTaskDraftBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * Every command, validation and decision uses real production HTTP and an owned
 * MySQL database. SQL creates only the task/finding input and deliberate failure
 * conditions; no draft, PASS, approval or publication output is fabricated.
 * The concurrency spy only holds a real acquired transaction lock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReturnedDraftRevisionHttpMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MAKER = S3CompoundMakerFixture.SUBJECT;
    private static final String CHECKER = "dev-external-qa-approver";
    private static final String PUBLISHER = "dev-external-publisher";
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

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private JdbcReviewTaskDraftBinding binding;
    private final List<Map<String, Object>> observations = new ArrayList<>();

    @BeforeAll
    void installOnlyPrivateCompoundIdentity() { S3CompoundMakerFixture.install(jdbc); }

    @AfterEach
    void clearOnlyTestLockObserver() { reset(binding); }

    @AfterAll
    void saveActualObservationsAndStopOnlyOwnedDatabase() throws Exception {
        try {
            String output = System.getProperty("s3.revision.evidence");
            if (output != null) {
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("mysql", jdbc.queryForMap("SELECT VERSION() AS version,DATABASE() AS database_name"));
                evidence.put("containerId", MYSQL.getContainerId());
                evidence.put("observations", observations);
                evidence.put("fixtureBoundary", "Task/finding inputs, one private compound identity and deliberate failure inputs only; all label, validation, decision and publication outputs are real HTTP");
                Files.writeString(Path.of(output), JSON.writeValueAsString(evidence), StandardCharsets.UTF_8);
            }
        } finally { MYSQL.stop(); }
    }

    @Test
    @Timeout(180)
    void immutableReturnedSnapshotRequiresNewIdentityFreshPassAndIndependentQaBeforePublication() throws Exception {
        Returned returned = returned("prod_usda_1106285");
        var before = allRows();
        expect(request("POST", submitPath(returned.labelId()), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).as("old genuine PASS cannot resubmit returned immutable ID").isEqualTo(before);
        // A newly executed PASS for the same old snapshot also cannot bypass the returned-ID guard.
        validate(returned.labelId(), returned.ruleSetId(), "PASSED");
        before = allRows();
        expect(request("POST", submitPath(returned.labelId()), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).isEqualTo(before);
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var call = connection.prepareCall("{call sp_submit_label_for_review(?,?)}")) {
            call.setString(1, returned.labelId());
            call.setString(2, S3CompoundMakerFixture.USER_ID);
            SQLException error = catchThrowableOfType(call::execute, SQLException.class);
            assertThat((Throwable) error).isNotNull();
            assertThat(error.getSQLState()).isEqualTo("45000");
            assertThat(error.getMessage()).contains("revision");
            assertThat(allRows()).as("actual installed procedure also rejects with zero writes").isEqualTo(before);
            observe("returnedProcedureDenied", Map.of("sqlState", error.getSQLState(), "message", error.getMessage()));
        }

        var oldSnapshot = immutableSnapshot(returned.labelId());
        var productBefore = product(returned.productId());
        var taskBefore = task(returned.taskId());
        var response = request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Corrected declaration"), MAKER);
        JsonNode revision = expect(response, 201, null);
        String newId = revision.get("labelVersionId").stringValue();
        assertThat(newId).isNotEqualTo(returned.labelId());
        assertThat(response.headers().firstValue("Location")).contains("/api/labels/" + newId);
        assertThat(revision.get("versionNumber").intValue()).isEqualTo(returned.version() + 1);
        assertThat(revision.get("createdByUserId").stringValue()).isEqualTo(S3CompoundMakerFixture.USER_ID);
        assertThat(revision.get("ruleSetVersionId").stringValue()).isEqualTo(returned.ruleSetId());
        assertThat(product(returned.productId())).as("revision never publishes or advances formula").isEqualTo(productBefore);
        assertThat(immutableSnapshot(returned.labelId())).as("returned content, PASS, decision and audit history unchanged").isEqualTo(oldSnapshot);
        var expectedTask = new LinkedHashMap<>(taskBefore);
        expectedTask.put("draft_label_version_id", newId);
        expectedTask.put("target_label_version_id", newId);
        expectedTask.put("decision", null);
        expectedTask.put("resolved_by_user_id", null);
        expectedTask.put("resolved_at", null);
        assertThat(task(returned.taskId())).isEqualTo(expectedTask);
        var readTask = expect(request("GET", "/api/review-tasks/" + returned.taskId(), null, MAKER), 200, null);
        assertThat(readTask.get("draftLabelVersionId").stringValue()).isEqualTo(newId);
        assertThat(readTask.get("targetLabelVersionId").stringValue()).isEqualTo(newId);
        assertThat(readTask.get("status").stringValue()).isEqualTo("OPEN");
        assertThat(readTask.get("decision").isNull()).isTrue();
        var audit = jdbc.queryForMap("SELECT * FROM audit_event WHERE entity_id=? AND event_type='LABEL_DRAFT_REVISED'", newId);
        JsonNode payload = JSON.readTree((String) audit.get("event_payload"));
        assertThat(payload.get("reviewTaskId").stringValue()).isEqualTo(returned.taskId());
        assertThat(payload.get("previousLabelVersionId").stringValue()).isEqualTo(returned.labelId());
        assertThat(payload.get("labelVersionId").stringValue()).isEqualTo(newId);
        assertThat(audit.get("actor_user_id")).isEqualTo(S3CompoundMakerFixture.USER_ID);

        before = allRows();
        expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Replay must not copy"), MAKER),
                409, "LABEL_VERSION_CONFLICT");
        expect(request("POST", submitPath(newId), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        expect(request("POST", "/api/review-tasks/" + returned.taskId() + "/publications",
                JSON.writeValueAsString(Map.of("labelVersionId", returned.labelId())), PUBLISHER),
                409, "LABEL_VERSION_CONFLICT");
        assertThat(allRows()).as("replay, wrong identity publication and inherited PASS change no rows").isEqualTo(before);
        // The old ID remains independently validatable, but its PASS cannot count for the new ID.
        validate(returned.labelId(), returned.ruleSetId(), "PASSED");
        before = allRows();
        expect(request("POST", submitPath(newId), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).isEqualTo(before);
        validate(newId, revision.get("ruleSetVersionId").stringValue(), "PASSED");
        expect(request("POST", submitPath(newId), "{}", MAKER), 200, null);
        before = allRows();
        JsonNode denied = expect(request("POST", decisionPath(newId), decision("APPROVE"), MAKER),
                403, "AUTHORIZATION_DENIED");
        assertThat(denied.get("message").stringValue()).isEqualTo(S3CompoundMakerFixture.POLICY_MESSAGE);
        assertThat(allRows()).as("compound creator has APPROVE permission but real maker-checker still denies").isEqualTo(before);
        expect(request("POST", decisionPath(newId), decision("APPROVE"), CHECKER), 200, null);
        expect(request("POST", "/api/review-tasks/" + returned.taskId() + "/publications",
                JSON.writeValueAsString(Map.of("labelVersionId", newId)), PUBLISHER), 200, null);
        assertThat(task(returned.taskId())).containsEntry("status", "CLOSED").containsEntry("decision", "APPROVE");
        assertThat(product(returned.productId())).containsEntry("current_published_label_version_id", newId)
                .containsEntry("current_formula_version_id", productBefore.get("current_formula_version_id"));
        assertThat(jdbc.queryForList("SELECT decided_by_user_id FROM approval_record WHERE label_version_id=? AND decision='APPROVE'",
                String.class, newId)).containsExactly("user_approver");
        assertThat(jdbc.queryForList("SELECT published_by_user_id FROM publication_record WHERE label_version_id=?",
                String.class, newId)).containsExactly("user_publisher");
        // There was one additional intentional old-ID validation after revision; all earlier immutable rows still match.
        assertExistingSnapshotRowsRemain(oldSnapshot, returned.labelId());
        observe("fullRevisionPublication", Map.of("taskId", returned.taskId(), "oldLabelId", returned.labelId(),
                "newLabelId", newId, "freshNewIdPass", true, "compoundMakerDenied", true,
                "independentQaPublished", true, "oldHistoricalRowsPreserved", true));
    }

    @Test
    @Timeout(120)
    void permissionsStrictInputAndWrongStageNeverCreateARevision() throws Exception {
        Returned returned = returned("prod_usda_1106963");
        var before = allRows();
        for (String subject : List.of(CHECKER, PUBLISHER, "dev-external-change-manager")) {
            expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Forbidden"), subject),
                    403, "AUTHORIZATION_DENIED");
            assertThat(allRows()).isEqualTo(before);
        }
        expect(request("POST", revisionPath("missing_revision_task"), revisionBody(returned, "Missing"), MAKER),
                404, "REVIEW_TASK_NOT_FOUND");
        expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned.labelId() + "_stale", returned.declarations(), "Stale"), MAKER),
                409, "LABEL_VERSION_CONFLICT");
        for (var invalid : List.of(Map.entry("{}", "LABEL_COMMAND_INVALID"),
                Map.entry("{\"expectedLabelVersionId\":\"" + returned.labelId() + "\",\"declarations\":[],\"actorUserId\":\"user_approver\"}", "LABEL_COMMAND_INVALID"),
                Map.entry("{\"expectedLabelVersionId\":\"" + returned.labelId() + "\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\"},{\"allergenId\":\"ALL_SOY\",\"declarationType\":\"CONTAINS\"}]}", "LABEL_DRAFT_INVALID"),
                Map.entry("{\"expectedLabelVersionId\":\"" + returned.labelId() + "\",\"declarations\":[{\"allergenId\":\"unknown_allergen\",\"declarationType\":\"CONTAINS\"}]}", "LABEL_DRAFT_INVALID"))) {
            expect(request("POST", revisionPath(returned.taskId()), invalid.getKey(), MAKER), 400, invalid.getValue());
            assertThat(allRows()).isEqualTo(before);
        }
        var revised = expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Valid correction"), MAKER), 201, null);
        String newId = revised.get("labelVersionId").stringValue();
        before = allRows();
        expect(request("POST", revisionPath(returned.taskId()), revisionBody(newId, returned.declarations(), "Not returned"), MAKER),
                409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).isEqualTo(before);
        observe("permissionAndStrictInputRejections", Map.of("taskId", returned.taskId(), "all19TablesUnchanged", true));
    }

    @Test
    @Timeout(120)
    void genuineMysqlForeignKeyFailureRollsBackNewLabelDeclarationsAndTaskAdvance() throws Exception {
        Returned returned = returned("prod_usda_1107123");
        var before = allRows();
        var sqlFailure = new AtomicReference<SQLException>();
        // Observe the real completed binding, then execute an actually invalid
        // audit write in that same transaction. No application output is mocked.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            try {
                jdbc.update("""
                        INSERT INTO audit_event(audit_event_id,event_type,entity_type,entity_id,
                            event_at,actor_user_id,event_payload,data_provenance_id)
                        VALUES ('private_revision_fk_failure','PRIVATE_TEST_FAILURE','LABEL_VERSION',?,
                            NOW(),'missing_private_revision_actor',JSON_OBJECT(),'prov_project_seed')
                        """, (String) invocation.getArgument(2));
            } catch (org.springframework.dao.DataIntegrityViolationException error) {
                if (error.getMostSpecificCause() instanceof SQLException cause) sqlFailure.set(cause);
                throw error;
            }
            return result;
        }).when(binding).bindReturnedTaskToRevision(eq(returned.taskId()), eq(returned.labelId()),
                anyString(), anyString(), anyString());
        try {
            JsonNode failure = expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Rollback correction"), MAKER),
                    500, "INTERNAL_ERROR");
            assertThat(failure.toString()).doesNotContain("missing_private_revision_actor");
            assertThat((Throwable) sqlFailure.get()).as("actual MySQL foreign key failure, not a fabricated exception").isNotNull();
            assertThat(sqlFailure.get().getSQLState()).isEqualTo("23000");
            assertThat(sqlFailure.get().getErrorCode()).isEqualTo(1452);
            assertThat(allRows()).as("real SQL failure happens after insertion and task update, all rows roll back").isEqualTo(before);
        } finally { reset(binding); }
        expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Correction after database recovery"), MAKER), 201, null);
        observe("realMysqlRollback", Map.of("taskId", returned.taskId(), "httpStatus", 500,
                "sqlState", sqlFailure.get().getSQLState(), "mysqlErrorCode", sqlFailure.get().getErrorCode(),
                "all19TablesUnchanged", true, "sameExpectedIdSucceededAfterFailureRemoved", true));
    }

    @Test
    @Timeout(120)
    void simultaneousHttpRevisionsHaveOneWinnerAndNoDetachedLosingDeclaration() throws Exception {
        Returned returned = returned("prod_usda_1109412");
        var oldSnapshot = immutableSnapshot(returned.labelId());
        var oldProduct = product(returned.productId());
        int labelsBefore = count("label_version");
        int declarationsBefore = count("label_allergen_declaration");
        int auditsBefore = count("audit_event");
        var firstLocked = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 2) secondEntered.countDown();
            Object result = invocation.callRealMethod();
            if (attempt == 1) {
                firstLocked.countDown();
                if (!releaseFirst.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Real revision lock timeout");
            }
            return result;
        }).when(binding).requireReturnedDraftAvailable(eq(returned.taskId()), eq(returned.labelId()));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Concurrent winner"), MAKER));
            assertThat(firstLocked.await(20, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Concurrent loser"), MAKER));
            assertThat(secondEntered.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).as("second real command waits on the existing product transaction lock").isFalse();
            releaseFirst.countDown();
            JsonNode winner = expect(first.get(30, TimeUnit.SECONDS), 201, null);
            expect(second.get(30, TimeUnit.SECONDS), 409, "LABEL_VERSION_CONFLICT");
            String id = winner.get("labelVersionId").stringValue();
            assertThat(task(returned.taskId())).containsEntry("draft_label_version_id", id).containsEntry("target_label_version_id", id);
            assertThat(count("label_version")).isEqualTo(labelsBefore + 1);
            assertThat(count("label_allergen_declaration")).isEqualTo(declarationsBefore + returned.declarations().size());
            assertThat(count("audit_event")).isEqualTo(auditsBefore + 1);
            assertThat(jdbc.queryForList("SELECT display_text FROM label_allergen_declaration WHERE label_version_id=?",
                    String.class, id)).allSatisfy(text -> assertThat(text).startsWith("Concurrent winner"));
            assertThat(immutableSnapshot(returned.labelId())).isEqualTo(oldSnapshot);
            assertThat(product(returned.productId())).isEqualTo(oldProduct);
            observe("realConcurrentRevisions", Map.of("taskId", returned.taskId(), "winnerLabelId", id,
                    "httpStatuses", List.of(201, 409), "newLabels", 1, "newRevisionAudits", 1, "oldSnapshotPreserved", true));
        } finally { releaseFirst.countDown(); }
    }

    @Test
    @Timeout(120)
    void staleFindingOrCurrentFormulaPreventsRevisionWithoutMutatingAnyOtherRows() throws Exception {
        Returned returned = returned("prod_usda_1110930");
        String previous = jdbc.queryForObject("SELECT proposed_formula_version_id FROM impact_finding WHERE impact_finding_id=?",
                String.class, returned.findingId());
        jdbc.update("UPDATE impact_finding SET proposed_formula_version_id='formula_1106285_v1' WHERE impact_finding_id=?", returned.findingId());
        var before = allRows();
        try {
            expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Stale finding"), MAKER),
                    409, "LABEL_VERSION_CONFLICT");
            assertThat(allRows()).isEqualTo(before);
        } finally {
            jdbc.update("UPDATE impact_finding SET proposed_formula_version_id=? WHERE impact_finding_id=?", previous, returned.findingId());
        }
        String formula = (String) product(returned.productId()).get("current_formula_version_id");
        jdbc.update("UPDATE product SET current_formula_version_id='formula_1106285_v1' WHERE product_id=?", returned.productId());
        before = allRows();
        try {
            expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "Stale formula"), MAKER),
                    409, "LABEL_VERSION_CONFLICT");
            assertThat(allRows()).isEqualTo(before);
        } finally { jdbc.update("UPDATE product SET current_formula_version_id=? WHERE product_id=?", formula, returned.productId()); }
        observe("staleInputRejections", Map.of("taskId", returned.taskId(), "findingAndFormulaRejected", true, "all19TablesUnchanged", true));
    }

    @Test
    @Timeout(120)
    void revisionPinsNewActiveRuleSetWithoutRetargetingOldLabelOrImpactRun() throws Exception {
        Returned returned = returned("prod_usda_1111174");
        String rule = "ruleset_private_revision_" + UUID.randomUUID().toString().replace("-", "");
        var oldSnapshot = immutableSnapshot(returned.labelId());
        String impactRule = jdbc.queryForObject("SELECT rule_set_version_id FROM impact_analysis_run WHERE impact_analysis_run_id=?",
                String.class, returned.runId());
        jdbc.update("""
                INSERT INTO rule_set_version (rule_set_version_id, rule_set_code, version_number,
                    jurisdiction_code, lifecycle_status, effective_from, effective_to, is_demo_only,
                    description, data_provenance_id)
                SELECT ?, ?, 'zz-private-new-version', jurisdiction_code, 'ACTIVE', CURRENT_DATE,
                       NULL, is_demo_only, 'Private RuleSet selection input, not validator output', data_provenance_id
                FROM rule_set_version WHERE rule_set_version_id=?
                """, rule, rule, returned.ruleSetId());
        try {
            JsonNode revision = expect(request("POST", revisionPath(returned.taskId()), revisionBody(returned, "New rules pin"), MAKER), 201, null);
            assertThat(revision.get("ruleSetVersionId").stringValue()).isEqualTo(rule);
            assertThat(immutableSnapshot(returned.labelId())).isEqualTo(oldSnapshot);
            assertThat(jdbc.queryForObject("SELECT rule_set_version_id FROM impact_analysis_run WHERE impact_analysis_run_id=?",
                    String.class, returned.runId())).isEqualTo(impactRule);
            observe("activeRuleSetSelection", Map.of("oldLabelRuleSetId", returned.ruleSetId(), "impactRuleSetId", impactRule,
                    "newLabelRuleSetId", rule, "oldPinsUnchanged", true));
        } finally { jdbc.update("UPDATE rule_set_version SET lifecycle_status='RETIRED' WHERE rule_set_version_id=?", rule); }
    }

    @Test
    @Timeout(120)
    void failingNewRevisionCannotBorrowGenuinePassingValidationFromReturnedOldId() throws Exception {
        Returned returned = returned("prod_usda_1114509");
        JsonNode revision = expect(request("POST", revisionPath(returned.taskId()),
                revisionBody(returned.labelId(), List.of(), "Missing allergen"), MAKER), 201, null);
        String id = revision.get("labelVersionId").stringValue();
        validate(id, revision.get("ruleSetVersionId").stringValue(), "FAILED");
        var before = allRows();
        expect(request("POST", submitPath(id), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).isEqualTo(before);
        assertThat(task(returned.taskId())).containsEntry("status", "OPEN").containsEntry("target_label_version_id", id);
        observe("failedNewRevisionValidation", Map.of("oldLabelId", returned.labelId(), "newLabelId", id,
                "oldRealPassRetained", true, "newRealFailedBlocksSubmission", true));
    }

    @Test
    @Timeout(120)
    void independentOlderDraftRemainsValidatableUnderExistingS2Contract() throws Exception {
        String product = "prod_usda_1117669";
        var declarations = publishedDeclarations(product);
        String first = expect(request("POST", "/api/labels/drafts", draftBody(product, null, declarations, "Older independent"), MAKER),
                201, null).get("labelVersionId").stringValue();
        String second = expect(request("POST", "/api/labels/drafts", draftBody(product, null, declarations, "Newer independent"), MAKER),
                201, null).get("labelVersionId").stringValue();
        String rule = jdbc.queryForObject("SELECT rule_set_version_id FROM label_version WHERE label_version_id=?", String.class, first);
        validate(first, rule, "PASSED");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, first)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, second)).isEqualTo("DRAFT");
        observe("olderDraftValidationRegression", Map.of("olderDraftId", first, "newerDraftId", second,
                "olderDraftRealValidation", "PASSED", "genericLatestDraftRestrictionAdded", false));
    }

    @Test
    @Timeout(120)
    void legacyProcedureReturnedHistoryWithNullDecisionCacheStillCannotResubmitOldId() throws Exception {
        Returned returned = returned("prod_usda_1123666", true);
        assertThat(task(returned.taskId())).containsEntry("status", "OPEN").containsEntry("decision", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE review_task_id=? "
                + "AND label_version_id=? AND decision='REQUEST_CHANGES'", Integer.class, returned.taskId(), returned.labelId())).isEqualTo(1);
        for (boolean targetOnlyLegacyBinding : List.of(false, true)) {
            if (targetOnlyLegacyBinding) {
                // Controlled historical input permitted by the existing submit/decision repository.
                jdbc.update("UPDATE review_task SET draft_label_version_id=NULL WHERE review_task_id=?", returned.taskId());
            }
            var before = allRows();
            expect(request("POST", submitPath(returned.labelId()), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
            assertThat(allRows()).isEqualTo(before);
            try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
                 var call = connection.prepareCall("{call sp_submit_label_for_review(?,?)}")) {
                call.setString(1, returned.labelId());
                call.setString(2, S3CompoundMakerFixture.USER_ID);
                SQLException error = catchThrowableOfType(call::execute, SQLException.class);
                assertThat((Throwable) error).isNotNull();
                assertThat(error.getSQLState()).isEqualTo("45000");
                assertThat(error.getMessage()).contains("revision");
                assertThat(allRows()).isEqualTo(before);
            }
        }
        var oldSnapshot = immutableSnapshot(returned.labelId());
        var oldProduct = product(returned.productId());
        JsonNode revised = expect(request("POST", revisionPath(returned.taskId()),
                revisionBody(returned, "Legacy returned declaration corrected"), MAKER), 201, null);
        String newId = revised.get("labelVersionId").stringValue();
        assertThat(newId).isNotEqualTo(returned.labelId());
        assertThat(task(returned.taskId())).containsEntry("status", "OPEN").containsEntry("decision", null)
                .containsEntry("draft_label_version_id", newId).containsEntry("target_label_version_id", newId);
        assertThat(immutableSnapshot(returned.labelId())).isEqualTo(oldSnapshot);
        assertThat(product(returned.productId())).isEqualTo(oldProduct);
        var audit = jdbc.queryForMap("SELECT before_value FROM audit_event WHERE entity_id=? AND event_type='LABEL_DRAFT_REVISED'", newId);
        JsonNode auditBefore = JSON.readTree((String) audit.get("before_value"));
        assertThat(auditBefore.get("draft_label_version_id").isNull()).as("audit retains actual legacy null draft reference").isTrue();
        assertThat(auditBefore.get("target_label_version_id").stringValue()).isEqualTo(returned.labelId());
        var before = allRows();
        expect(request("POST", submitPath(newId), "{}", MAKER), 409, "LABEL_WORKFLOW_CONFLICT");
        assertThat(allRows()).isEqualTo(before);
        validate(newId, revised.get("ruleSetVersionId").stringValue(), "PASSED");
        expect(request("POST", submitPath(newId), "{}", MAKER), 200, null);
        expect(request("POST", decisionPath(newId), decision("APPROVE"), CHECKER), 200, null);
        expect(request("POST", "/api/review-tasks/" + returned.taskId() + "/publications",
                JSON.writeValueAsString(Map.of("labelVersionId", newId)), PUBLISHER), 200, null);
        assertThat(task(returned.taskId())).containsEntry("status", "CLOSED").containsEntry("decision", "APPROVE");
        assertThat(product(returned.productId())).containsEntry("current_published_label_version_id", newId);
        assertThat(immutableSnapshot(returned.labelId())).isEqualTo(oldSnapshot);
        observe("legacyDirectDecisionFallback", Map.of("taskId", returned.taskId(), "labelId", returned.labelId(),
                "taskDecisionCacheNull", true, "actualRequestChangesHistory", 1, "bindingVariants", List.of("draft-and-target", "target-only"),
                "httpSubmitStatus", 409, "procedureSubmitSqlState", "45000", "all19TablesUnchangedAfterDenials", true,
                "realNewRevisionPublicationId", newId, "legacyHistoryPreserved", true));
    }

    @Test
    @Timeout(120)
    void revisionWaitingOnProductLockUsesRulesActivatedAfterItsInitialTaskLookup() throws Exception {
        String product = jdbc.queryForObject("""
                SELECT p.product_id FROM product p
                JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id
                WHERE lv.jurisdiction_code='US'
                  AND NOT EXISTS (SELECT 1 FROM review_task rt WHERE rt.product_id=p.product_id)
                  AND EXISTS (SELECT 1 FROM label_allergen_declaration d WHERE d.label_version_id=lv.label_version_id)
                ORDER BY p.product_id DESC LIMIT 1
                """, String.class);
        Returned returned = returned(product);
        var oldSnapshot = immutableSnapshot(returned.labelId());
        String newRule = "ruleset_lock_activation_" + UUID.randomUUID().toString().replace("-", "");
        var revisionEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            revisionEntered.countDown();
            return invocation.callRealMethod();
        }).when(binding).requireReturnedDraftAvailable(eq(returned.taskId()), eq(returned.labelId()));
        try (var holder = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            try {
                try (var lock = holder.prepareStatement("SELECT product_id FROM product WHERE product_id=? FOR UPDATE")) {
                    lock.setString(1, product);
                    try (var locked = lock.executeQuery()) { assertThat(locked.next()).isTrue(); }
                }
                var revision = executor.submit(() -> request("POST", revisionPath(returned.taskId()),
                        revisionBody(returned, "Correction after rules activation"), MAKER));
                assertThat(revisionEntered.await(20, TimeUnit.SECONDS)).isTrue();
                String waitingSql = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (waitingSql == null && System.nanoTime() < deadline) {
                    for (var process : jdbc.queryForList("SHOW FULL PROCESSLIST")) {
                        Object info = process.get("Info");
                        if (info instanceof String sql) {
                            String normalized = sql.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
                            if (normalized.contains("select product_id from product where product_id")
                                    && normalized.contains("for update")) waitingSql = sql;
                        }
                    }
                    if (waitingSql == null) TimeUnit.MILLISECONDS.sleep(25);
                }
                assertThat(waitingSql).as("actual product locking query reached after the real plain task lookup").isNotNull();
                assertThat(revision.isDone()).as("revision waits while the holder owns the product lock").isFalse();
                try (var insert = holder.prepareStatement("""
                        INSERT INTO rule_set_version(rule_set_version_id,rule_set_code,version_number,
                            jurisdiction_code,lifecycle_status,effective_from,effective_to,is_demo_only,
                            description,data_provenance_id)
                        SELECT ?,?,'zz-lock-activation',jurisdiction_code,'ACTIVE',CURRENT_DATE,NULL,
                               is_demo_only,'Private actual rule activation during product lock wait',data_provenance_id
                        FROM rule_set_version WHERE rule_set_version_id=?
                        """)) {
                    insert.setString(1, newRule); insert.setString(2, newRule); insert.setString(3, returned.ruleSetId());
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (var definitions = holder.prepareStatement("""
                        INSERT INTO rule_definition(rule_definition_id,rule_set_version_id,rule_code,rule_type,
                            target_allergen_id,pattern_text,severity,is_active,description)
                        SELECT CONCAT('rule_activation_',REPLACE(UUID(),'-','')),?,rule_code,rule_type,
                               target_allergen_id,pattern_text,severity,is_active,description
                        FROM rule_definition WHERE rule_set_version_id=?
                        """)) {
                    definitions.setString(1, newRule); definitions.setString(2, returned.ruleSetId());
                    assertThat(definitions.executeUpdate()).isPositive();
                }
                try (var retire = holder.prepareStatement("UPDATE rule_set_version SET lifecycle_status='RETIRED' WHERE rule_set_version_id=?")) {
                    retire.setString(1, returned.ruleSetId()); assertThat(retire.executeUpdate()).isEqualTo(1);
                }
                holder.commit();
                JsonNode created = expect(revision.get(30, TimeUnit.SECONDS), 201, null);
                String newId = created.get("labelVersionId").stringValue();
                assertThat(created.get("ruleSetVersionId").stringValue()).isEqualTo(newRule);
                assertThat(created.get("versionNumber").intValue()).isEqualTo(returned.version() + 1);
                assertThat(immutableSnapshot(returned.labelId())).isEqualTo(oldSnapshot);
                validate(newId, newRule, "PASSED");
                observe("rulesActivationWhileRevisionWaits", Map.of("taskId", returned.taskId(),
                        "oldLabelRuleSetId", returned.ruleSetId(), "newRevisionRuleSetId", newRule,
                        "newLabelId", newId, "actualBlockedProductQuery", waitingSql,
                        "activationCommittedAfterTaskLookup", true, "realNewRuleSetValidation", "PASSED",
                        "oldImmutableSnapshotPreserved", true));
            } finally {
                holder.rollback();
                // Restore only this disposable test's temporary rules input.
                jdbc.update("UPDATE rule_set_version SET lifecycle_status='ACTIVE' WHERE rule_set_version_id=?", returned.ruleSetId());
                jdbc.update("UPDATE rule_set_version SET lifecycle_status='RETIRED' WHERE rule_set_version_id=?", newRule);
            }
        }
    }

    private Returned returned(String product) throws Exception {
        return returned(product, false);
    }

    private Returned returned(String product, boolean legacyDirectDecision) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String change = "revision_change_" + suffix;
        String run = "revision_run_" + suffix;
        String finding = "revision_finding_" + suffix;
        String task = "revision_task_" + suffix;
        jdbc.update("""
                INSERT INTO change_request (change_request_id, change_request_code, change_type, status,
                    requested_at, requested_by_user_id, description, from_formula_version_id,
                    to_formula_version_id, data_provenance_id)
                SELECT ?, ?, 'FORMULA', 'ANALYZED', NOW(), 'user_label_officer',
                       'Private returned-draft review input', current_formula_version_id,
                       current_formula_version_id, data_provenance_id FROM product WHERE product_id=?
                """, change, change, product);
        jdbc.update("""
                INSERT INTO impact_analysis_run (impact_analysis_run_id, run_code, change_request_id,
                    idempotency_key, rule_set_version_id, status, started_at, completed_at,
                    executed_by_user_id, data_provenance_id)
                SELECT ?, ?, ?, ?, lv.rule_set_version_id, 'COMPLETED', NOW(), NOW(),
                       'user_label_officer', p.data_provenance_id FROM product p
                JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id
                WHERE p.product_id=?
                """, run, run, change, "impact-analysis:" + change, product);
        jdbc.update("""
                INSERT INTO impact_finding (impact_finding_id, impact_analysis_run_id, product_id,
                    current_formula_version_id, proposed_formula_version_id, current_label_version_id,
                    classification, missing_allergen_codes, explanation, data_provenance_id)
                SELECT ?, ?, p.product_id, p.current_formula_version_id, p.current_formula_version_id,
                       p.current_published_label_version_id, 'REVIEW_REQUIRED', JSON_ARRAY(),
                       'Private returned-draft task input', p.data_provenance_id FROM product p WHERE p.product_id=?
                """, finding, run, product);
        jdbc.update("""
                INSERT INTO review_task (review_task_id, impact_finding_id, product_id,
                    current_label_version_id, draft_label_version_id, target_label_version_id,
                    status, assigned_to_user_id, created_by_user_id, created_at, data_provenance_id)
                SELECT ?, ?, product_id, current_published_label_version_id, NULL, NULL, 'OPEN',
                       'user_approver', 'user_label_officer', NOW(), data_provenance_id FROM product WHERE product_id=?
                """, task, finding, product);
        List<String> declarations = publishedDeclarations(product);
        assertThat(declarations).as("known seeded product has genuine allergen declarations").isNotEmpty();
        JsonNode draft = expect(request("POST", "/api/labels/drafts", draftBody(product, task, declarations, "Original declaration"), MAKER), 201, null);
        String id = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        validate(id, rule, "PASSED");
        expect(request("POST", submitPath(id), "{}", MAKER), 200, null);
        if (legacyDirectDecision) {
            jdbc.update("CALL sp_record_label_decision(?,?,?,?)", id, "user_approver", "REQUEST_CHANGES", "Real legacy direct procedure decision");
        } else {
            expect(request("POST", decisionPath(id), decision("REQUEST_CHANGES"), CHECKER), 200, null);
        }
        assertThat(task(task)).containsEntry("status", "OPEN").containsEntry("decision", legacyDirectDecision ? null : "REQUEST_CHANGES")
                .containsEntry("draft_label_version_id", id).containsEntry("target_label_version_id", id);
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, id)).isEqualTo("DRAFT");
        return new Returned(product, task, finding, run, id, rule, draft.get("versionNumber").intValue(), declarations);
    }

    private void validate(String id, String rule, String status) throws Exception {
        JsonNode result = expect(request("POST", "/api/v1/label-versions/" + id + "/validation-runs",
                JSON.writeValueAsString(Map.of("ruleSetVersionId", rule)), MAKER), 201, null);
        assertThat(result.get("status").stringValue()).as("genuine evaluator response for %s: %s", id, result).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT label_version_id FROM validation_run WHERE validation_run_id=?",
                String.class, result.get("validationRunId").stringValue())).isEqualTo(id);
    }

    private List<String> publishedDeclarations(String product) {
        return jdbc.queryForList("SELECT allergen_id FROM label_allergen_declaration WHERE label_version_id="
                + "(SELECT current_published_label_version_id FROM product WHERE product_id=?) ORDER BY allergen_id", String.class, product);
    }

    private String draftBody(String product, String task, List<String> declarations, String display) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("productId", product);
        body.put("jurisdictionCode", "US");
        if (task != null) body.put("reviewTaskId", task);
        body.put("declarations", declarationPayload(declarations, display));
        return JSON.writeValueAsString(body);
    }

    private String revisionBody(Returned returned, String display) throws Exception {
        return revisionBody(returned.labelId(), returned.declarations(), display);
    }

    private String revisionBody(String expected, List<String> declarations, String display) throws Exception {
        return JSON.writeValueAsString(Map.of("expectedLabelVersionId", expected,
                "declarations", declarationPayload(declarations, display)));
    }

    private List<Map<String, String>> declarationPayload(List<String> declarations, String display) {
        return declarations.stream().map(id -> Map.of("allergenId", id, "declarationType", "CONTAINS", "displayText", display + " " + id)).toList();
    }

    private Map<String, List<Map<String, Object>>> allRows() { return S3CompoundMakerFixture.allRows(jdbc); }
    private Map<String, Object> product(String id) { return jdbc.queryForMap("SELECT * FROM product WHERE product_id=?", id); }
    private Map<String, Object> task(String id) { return jdbc.queryForMap("SELECT * FROM review_task WHERE review_task_id=?", id); }
    private int count(String table) { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)); }

    private Map<String, Object> immutableSnapshot(String label) {
        var result = new LinkedHashMap<String, Object>();
        result.put("label", jdbc.queryForMap("SELECT * FROM label_version WHERE label_version_id=?", label));
        result.put("declarations", jdbc.queryForList("SELECT * FROM label_allergen_declaration WHERE label_version_id=? ORDER BY 1", label));
        result.put("validationRuns", jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label));
        result.put("validationResults", jdbc.queryForList("SELECT * FROM validation_result WHERE validation_run_id IN"
                + " (SELECT validation_run_id FROM validation_run WHERE label_version_id=?) ORDER BY 1", label));
        result.put("approvals", jdbc.queryForList("SELECT * FROM approval_record WHERE label_version_id=? ORDER BY 1", label));
        result.put("publications", jdbc.queryForList("SELECT * FROM publication_record WHERE label_version_id=? ORDER BY 1", label));
        result.put("audits", jdbc.queryForList("SELECT * FROM audit_event WHERE entity_id=? ORDER BY 1", label));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void assertExistingSnapshotRowsRemain(Map<String, Object> before, String label) {
        var after = immutableSnapshot(label);
        assertThat(after.get("label")).isEqualTo(before.get("label"));
        for (String key : List.of("declarations", "validationRuns", "validationResults", "approvals", "publications", "audits")) {
            assertThat((List<Map<String, Object>>) after.get(key)).containsAll((List<Map<String, Object>>) before.get(key));
        }
    }

    private HttpResponse<String> request(String method, String endpoint, String body, String subject) throws Exception {
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                    .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json")
                    .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject)
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private JsonNode expect(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as("HTTP %s body %s", response.request().uri(), response.body()).isEqualTo(status);
        JsonNode json = JSON.readTree(response.body());
        if (code != null) assertThat(json.get("code").stringValue()).isEqualTo(code);
        return json;
    }

    private void observe(String name, Map<String, Object> facts) {
        var record = new LinkedHashMap<String, Object>();
        record.put("name", name);
        record.putAll(facts);
        observations.add(record);
    }
    private String revisionPath(String task) { return "/api/review-tasks/" + task + "/draft-revisions"; }
    private String submitPath(String label) { return "/api/labels/" + label + "/review-submissions"; }
    private String decisionPath(String label) { return "/api/labels/" + label + "/review-decisions"; }
    private String decision(String value) throws Exception {
        return JSON.writeValueAsString(Map.of("decision", value, "comments", "Real returned-draft revision acceptance"));
    }
    private record Returned(String productId, String taskId, String findingId, String runId,
                            String labelId, String ruleSetId, int version, List<String> declarations) { }
}
