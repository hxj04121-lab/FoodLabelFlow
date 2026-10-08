package com.spectrace.workflow;

import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.infrastructure.JdbcValidationRunRepository;
import com.spectrace.validation.infrastructure.JdbcValidationResultRepository;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.workflow.infrastructure.JdbcLabelReviewCommandRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/** Real MySQL persisted input fixtures and production HTTP consumption, with no mocked command output. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ValidationRunOrderingIntegrationTest.FixedValidationClock.class)
class ValidationRunOrderingIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MAKER = S3CompoundMakerFixture.SUBJECT;
    private static final String SAME_SECOND = "2026-10-08 12:00:00";
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
    @MockitoSpyBean private JdbcValidationRunRepository runPersistence;
    @MockitoSpyBean private JdbcValidationResultRepository resultPersistence;
    @MockitoSpyBean private JdbcLabelReviewCommandRepository reviewCommands;
    private final List<Map<String, Object>> observations = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedValidationClock {
        @Bean @Primary
        ValidationApplicationService realValidationWithFixedClock(ValidationOrchestrator orchestrator,
                ValidationRunRepository runs, ValidationResultRepository results, ValidationIntegration integration) {
            return new ValidationApplicationService(orchestrator, runs, results, integration,
                    Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC));
        }
    }

    @BeforeAll
    void installPrivateIdentity() { S3CompoundMakerFixture.install(jdbc); }

    @AfterEach
    void resetOnlyTestObservers() { reset(runPersistence, resultPersistence, reviewCommands); }

    @AfterAll
    void saveEvidenceAndStopOnlyOwnedDatabase() throws Exception {
        try {
            String output = System.getProperty("validation.order.evidence");
            if (output != null) {
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("mysql", jdbc.queryForMap("SELECT VERSION() AS version,DATABASE() AS database_name"));
                evidence.put("containerId", MYSQL.getContainerId());
                evidence.put("baselineObservationMode", Boolean.getBoolean("validation.order.expectBaseline"));
                evidence.put("fixtureBoundary", "Real MySQL sequentially committed validation_run input rows with deliberately fixed opposing UUID order; draft and submit outputs use real production HTTP. This is not a claim of randomly generated UUID occurrence or a mocked database.");
                evidence.put("observations", observations);
                Files.writeString(Path.of(output), JSON.writeValueAsString(evidence), StandardCharsets.UTF_8);
            }
        } finally { MYSQL.stop(); }
    }

    @Test
    @Timeout(120)
    void laterFailedValidationBlocksSubmissionDespiteLexicallyLargerOlderPassedUuid() throws Exception {
        verifyDirection("prod_usda_1106285", "PASSED", "FAILED", 409, 200);
    }

    @Test
    @Timeout(120)
    void laterPassedValidationAllowsSubmissionDespiteLexicallyLargerOlderFailedUuid() throws Exception {
        verifyDirection("prod_usda_1106963", "FAILED", "PASSED", 200, 409);
    }

    @Test @Timeout(120)
    void directSqlSubmissionUsesTheExactCurrentRunAndPreservesPriorEvidence() throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_sql_pass", label, rule, "PASSED", "Older SQL input");
        insertRun("11111111_sql_fail", label, rule, "FAILED", "Later SQL input");
        var before = allPersistenceRows();
        SQLException error;
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var call = connection.prepareCall("{call sp_submit_label_for_review(?,?)}")) {
            call.setString(1, label); call.setString(2, S3CompoundMakerFixture.USER_ID);
            error = catchThrowableOfType(call::execute, SQLException.class);
        }
        assertThat((Throwable) error).isNotNull();
        assertThat(error.getSQLState()).isEqualTo("45000");
        assertThat(allPersistenceRows()).isEqualTo(before);
        assertThat(current(label, rule)).containsEntry("validation_run_id", "11111111_sql_fail");
        insertRun("00000000_sql_new_pass", label, rule, "PASSED", "Third SQL input");
        var history = jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label);
        jdbc.update("CALL sp_submit_label_for_review(?,?)", label, S3CompoundMakerFixture.USER_ID);
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, label)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label)).isEqualTo(history);
        observe("directSqlLatestRunGuard", Map.of("labelVersionId", label, "failureSqlState", error.getSQLState(),
                "laterPassAccepted", true, "current", current(label, rule), "historyUnchanged", true));
    }

    @Test @Timeout(120)
    void latestFailedRunBlocksApprovalAndPublicationWithoutAnyHistoricalMutation() throws Exception {
        var approvalDraft = draftWithTask(unusedProduct());
        String approvalLabel = approvalDraft.get("labelVersionId").stringValue();
        String approvalRule = approvalDraft.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_approve_pass", approvalLabel, approvalRule, "PASSED", "Before submission");
        expect(request("POST", "/api/labels/" + approvalLabel + "/review-submissions", "{}"), 200);
        insertRun("11111111_approve_fail", approvalLabel, approvalRule, "FAILED", "After submission");
        var before = allPersistenceRows();
        var deniedApproval = requestAs("POST", "/api/labels/" + approvalLabel + "/review-decisions",
                "{\"decision\":\"APPROVE\",\"comments\":\"Actual independent checker\"}", "dev-external-qa-approver");
        expect(deniedApproval, 409);
        assertThat(allPersistenceRows()).isEqualTo(before);

        var publicationDraft = draftWithTask(unusedProduct());
        String publishedLabel = publicationDraft.get("labelVersionId").stringValue();
        String publicationRule = publicationDraft.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_publish_pass", publishedLabel, publicationRule, "PASSED", "Before approval");
        expect(request("POST", "/api/labels/" + publishedLabel + "/review-submissions", "{}"), 200);
        expect(requestAs("POST", "/api/labels/" + publishedLabel + "/review-decisions",
                "{\"decision\":\"APPROVE\",\"comments\":\"Actual independent checker\"}", "dev-external-qa-approver"), 200);
        insertRun("11111111_publish_fail", publishedLabel, publicationRule, "FAILED", "After approval");
        String task = jdbc.queryForObject("SELECT review_task_id FROM review_task WHERE draft_label_version_id=?", String.class, publishedLabel);
        before = allPersistenceRows();
        var deniedPublication = requestAs("POST", "/api/review-tasks/" + task + "/publications",
                JSON.writeValueAsString(Map.of("labelVersionId", publishedLabel)), "dev-external-publisher");
        expect(deniedPublication, 409);
        assertThat(allPersistenceRows()).isEqualTo(before);
        observe("approvalAndPublicationRecheck", Map.of("approvalLabel", approvalLabel,
                "approvalHttp", deniedApproval.statusCode(), "publicationLabel", publishedLabel,
                "publicationHttp", deniedPublication.statusCode(), "all21HistoricalTablesUnchangedOnRejection", true));
    }

    @Test @Timeout(120)
    void uncommittedFailedRunDoesNotAdvanceVisibleCurrentPointerAndRollbackUnblocksSubmission() throws Exception {
        transactionOrdering(false);
    }

    @Test @Timeout(120)
    void committedFailedRunWinsBeforeBlockedSubmissionAndCannotBeHiddenByAnOldSnapshot() throws Exception {
        transactionOrdering(true);
    }

    @Test @Timeout(120)
    void realValidationFailureAfterResultsAndPointerInsertionRollsBackAllTwentyOneTables() throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        String path = "/api/v1/label-versions/" + label + "/validation-runs";
        String body = JSON.writeValueAsString(Map.of("ruleSetVersionId", rule));
        var oldRun = expect(request("POST", path, body), 201).get("validationRunId").stringValue();
        var before = allPersistenceRows();
        String oldGet = request("GET", "/api/v1/validation-runs/" + oldRun, null).body();
        var observedPointer = new AtomicReference<Map<String, Object>>();
        var sqlFailure = new AtomicReference<SQLException>();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            String run = invocation.getArgument(0);
            observedPointer.set(current(label, rule));
            assertThat(observedPointer.get()).containsEntry("validation_run_id", run);
            try {
                jdbc.update("""
                        INSERT INTO validation_result(validation_result_id,validation_run_id,rule_definition_id,
                            result_code,severity,passed,blocking,message)
                        VALUES ('private_order_fk_failure',?,'missing_private_order_rule','PRIVATE_FAILURE','ERROR','N','Y','Real FK failure input')
                        """, run);
            } catch (org.springframework.dao.DataIntegrityViolationException error) {
                if (error.getMostSpecificCause() instanceof SQLException cause) sqlFailure.set(cause);
                throw error;
            }
            return result;
        }).when(resultPersistence).saveAll(anyString(), anyList());
        try {
            var failure = request("POST", path, body);
            expect(failure, 500);
            assertThat((Throwable) sqlFailure.get()).isNotNull();
            assertThat(sqlFailure.get().getSQLState()).isEqualTo("23000");
            assertThat(sqlFailure.get().getErrorCode()).isEqualTo(1452);
            assertThat(allPersistenceRows()).as("run, results, pointer, audit and every prior row all roll back").isEqualTo(before);
            assertThat(request("GET", "/api/v1/validation-runs/" + oldRun, null).body()).isEqualTo(oldGet);
            observe("realValidationFullTransactionRollback", Map.of("labelVersionId", label,
                    "httpStatus", failure.statusCode(), "sqlState", sqlFailure.get().getSQLState(),
                    "mysqlErrorCode", sqlFailure.get().getErrorCode(), "pointerObservedInsideFailedTransaction", observedPointer.get(),
                    "all21TablesUnchangedAfterRollback", true, "oldGetByteIdentical", true));
        } finally { reset(resultPersistence); }
        expect(request("POST", path, body), 201);
    }

    @Test @Timeout(120)
    void twoConcurrentRealValidationPostsSerializeAndDuplicateRunReplayCannotAdvancePointer() throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        String path = "/api/v1/label-versions/" + label + "/validation-runs";
        String body = JSON.writeValueAsString(Map.of("ruleSetVersionId", rule));
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                inserted.countDown();
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Private transaction observer timed out");
            }
            return result;
        }).when(runPersistence).save(any(ValidationRun.class));
        String waitingSql;
        JsonNode first;
        JsonNode second;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResponse = executor.submit(() -> request("POST", path, body));
            assertThat(inserted.await(20, TimeUnit.SECONDS)).isTrue();
            var secondResponse = executor.submit(() -> request("POST", path, body));
            waitingSql = awaitProductWait();
            assertThat(secondResponse.isDone()).isFalse();
            release.countDown();
            first = expect(firstResponse.get(30, TimeUnit.SECONDS), 201);
            second = expect(secondResponse.get(30, TimeUnit.SECONDS), 201);
        } finally { release.countDown(); reset(runPersistence); }
        String firstId = first.get("validationRunId").stringValue();
        String secondId = second.get("validationRunId").stringValue();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(second.get("ranAt")).isEqualTo(first.get("ranAt"));
        assertThat(current(label, rule)).containsEntry("validation_run_id", secondId);
        assertThat(((Number) current(label, rule).get("current_sequence")).longValue()).isEqualTo(2L);
        assertThat(((Number) current(label, rule).get("observed_run_count")).longValue()).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT registration_sequence FROM validation_run_registration WHERE validation_run_id=?", Long.class, firstId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT registration_sequence FROM validation_run_registration WHERE validation_run_id=?", Long.class, secondId)).isEqualTo(2L);
        var before = allPersistenceRows();
        var duplicate = catchThrowableOfType(() -> insertRun(secondId, label, rule, "FAILED", "Duplicate replay input"),
                org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.getMostSpecificCause()).isInstanceOf(SQLException.class);
        SQLException error = (SQLException) duplicate.getMostSpecificCause();
        assertThat(error.getSQLState()).isEqualTo("23000");
        assertThat(error.getErrorCode()).isEqualTo(1062);
        assertThat(allPersistenceRows()).isEqualTo(before);
        observe("concurrentRealValidationAndDuplicateReplay", Map.of("labelVersionId", label,
                "firstRun", firstId, "secondRun", secondId, "sameSecond", first.get("ranAt"),
                "actualBlockedSql", waitingSql, "current", current(label, rule),
                "duplicateSqlState", error.getSQLState(), "duplicateMySqlCode", error.getErrorCode(),
                "duplicateAll21TablesUnchanged", true));
    }

    @Test @Timeout(180)
    void directSqlWorkflowUsesProductFirstLockAgainstUncommittedValidationAndRespectsCommitOrRollback() throws Exception {
        for (boolean commit : List.of(true, false)) {
            var draft = draftWithTask(unusedProduct());
            String label = draft.get("labelVersionId").stringValue();
            String rule = draft.get("ruleSetVersionId").stringValue();
            String suffix = UUID.randomUUID().toString().replace("-", "");
            insertRun("ffffffff_direct_" + suffix, label, rule, "PASSED", "Committed input before direct SQL race");
            try (var holder = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
                 var executor = Executors.newSingleThreadExecutor()) {
                holder.setAutoCommit(false);
                try {
                    insertRun(holder, "11111111_direct_" + suffix, label, rule, "FAILED");
                    var call = executor.submit(() -> {
                        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
                             var statement = connection.prepareCall("{call sp_submit_label_for_review(?,?)}")) {
                            statement.setString(1, label); statement.setString(2, S3CompoundMakerFixture.USER_ID);
                            statement.execute(); return "SUCCESS";
                        } catch (SQLException failure) { return failure.getSQLState() + ":" + failure.getErrorCode(); }
                    });
                    String sql = awaitProductWait();
                    assertThat(call.isDone()).isFalse();
                    if (commit) holder.commit(); else holder.rollback();
                    String actual = call.get(30, TimeUnit.SECONDS);
                    if (commit) assertThat(actual).startsWith("45000:"); else assertThat(actual).isEqualTo("SUCCESS");
                    observe("directSqlProductFirstRace", Map.of("labelVersionId", label,
                            "validationTransactionCommitted", commit, "actualBlockedSql", sql,
                            "actualProcedureOutcome", actual, "current", current(label, rule)));
                } finally { holder.rollback(); }
            }
        }
    }

    @Test @Timeout(120)
    void unsupportedBareSqlRunFailsClosedCannotBeRegisteredLateAndARealNewValidationRestoresQualification() throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_supported_before_bare", label, rule, "PASSED", "Canonical committed input");
        bareInsertRun("11111111_unregistered_fail", label, rule, "FAILED");
        var before = allPersistenceRows();
        var rejected = request("POST", "/api/labels/" + label + "/review-submissions", "{}");
        expect(rejected, 409);
        assertThat(allPersistenceRows()).isEqualTo(before);
        var lateRegistration = catchThrowableOfType(() -> insertRun("11111111_unregistered_fail", label, rule,
                "PASSED", "Must not register an existing raw run"), org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(lateRegistration).isNotNull();
        assertThat(((SQLException) lateRegistration.getMostSpecificCause()).getErrorCode()).isEqualTo(1062);
        assertThat(allPersistenceRows()).as("duplicate PK does not retroactively trust an existing raw row").isEqualTo(before);
        var recovery = expect(request("POST", "/api/v1/label-versions/" + label + "/validation-runs",
                JSON.writeValueAsString(Map.of("ruleSetVersionId", rule))), 201);
        assertThat(recovery.get("status").stringValue()).isEqualTo("PASSED");
        assertThat(current(label, rule)).containsEntry("validation_run_id", recovery.get("validationRunId").stringValue());
        assertThat(((Number) current(label, rule).get("observed_run_count")).longValue()).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run_registration WHERE validation_run_id='11111111_unregistered_fail'", Integer.class)).isZero();
        expect(request("POST", "/api/labels/" + label + "/review-submissions", "{}"), 200);
        observe("bareSqlFailClosedAndFreshValidationRecovery", Map.of("labelVersionId", label,
                "rawFailureRun", "11111111_unregistered_fail", "bareRunRejectedHttp", rejected.statusCode(),
                "lateRegistrationRejectedMySql", 1062, "freshRecoveryRun", recovery.get("validationRunId").stringValue(),
                "current", current(label, rule), "historicalRawFailurePreserved", true));
    }

    @Test @Timeout(120)
    void bareSqlInsertActuallyWaitsOnConsumerLabelLockAndLaterUnregisteredFailureBlocksApproval() throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_before_bare_race", label, rule, "PASSED", "Before consumer lock");
        var readComplete = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            assertThat(result).isEqualTo(true);
            readComplete.countDown();
            if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Private consumer observer timed out");
            return result;
        }).when(reviewCommands).hasPassingValidation(eq(label), eq(rule));
        String actualWait;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var submission = executor.submit(() -> request("POST", "/api/labels/" + label + "/review-submissions", "{}"));
            assertThat(readComplete.await(20, TimeUnit.SECONDS)).isTrue();
            var rawWrite = executor.submit(() -> bareInsertRun("11111111_bare_race_fail", label, rule, "FAILED"));
            actualWait = awaitDatabaseWait("label_version", "insert into validation_run");
            assertThat(rawWrite.isDone()).as("actual raw insert waits on label FK shared lock while consumer holds label X").isFalse();
            release.countDown();
            expect(submission.get(30, TimeUnit.SECONDS), 200);
            assertThat(rawWrite.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        } finally { release.countDown(); reset(reviewCommands); }
        var before = allPersistenceRows();
        var approval = requestAs("POST", "/api/labels/" + label + "/review-decisions", "{\"decision\":\"APPROVE\"}", "dev-external-qa-approver");
        expect(approval, 409);
        assertThat(allPersistenceRows()).isEqualTo(before);
        observe("actualBareInsertWaitsForConsumer", Map.of("labelVersionId", label,
                "actualLabelFkLockWait", actualWait, "submissionCommittedBeforeBareInsert", true,
                "laterUnregisteredFailureApprovalHttp", approval.statusCode(), "noTriggerClaim", true));
    }

    private void transactionOrdering(boolean commit) throws Exception {
        var draft = draftWithTask(unusedProduct());
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String old = "ffffffff_" + suffix;
        String later = "11111111_" + suffix;
        insertRun(old, label, rule, "PASSED", "Committed older pass");
        var oldPointer = current(label, rule);
        var oldRuns = jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label);
        try (var holder = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            try {
                insertRun(holder, later, label, rule, "FAILED");
                assertThat(current(label, rule)).as("uncommitted pointer cannot leak through a plain outside read").isEqualTo(oldPointer);
                var submission = executor.submit(() -> request("POST", "/api/labels/" + label + "/review-submissions", "{}"));
                String waitingSql = awaitProductWait();
                assertThat(submission.isDone()).as("HTTP command actually waits behind insert trigger's product lock").isFalse();
                if (commit) holder.commit(); else holder.rollback();
                var result = submission.get(30, TimeUnit.SECONDS);
                expect(result, commit ? 409 : 200);
                if (commit) {
                    assertThat(current(label, rule)).containsEntry("validation_run_id", later);
                    assertThat(jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label)).containsAll(oldRuns);
                } else {
                    assertThat(current(label, rule)).isEqualTo(oldPointer);
                    assertThat(jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY 1", label)).isEqualTo(oldRuns);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run WHERE validation_run_id=?", Integer.class, later)).isZero();
                }
                observe(commit ? "committedFailureBeforeWaitingSubmit" : "rollbackNeverPublishesPointer",
                        Map.of("labelVersionId", label, "oldRun", old, "laterRun", later,
                                "actualBlockedSql", waitingSql, "uncommittedPointerInvisible", true,
                                "httpStatus", result.statusCode(), "current", current(label, rule)));
            } finally { holder.rollback(); }
        }
    }

    @Test @Timeout(120)
    void realValidationPostWaitingOnProductLockReadsNewlyCommittedAllergenMappingAndRetainsOldGetContract() throws Exception {
        String product = jdbc.queryForObject("""
                SELECT DISTINCT p.product_id FROM product p JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id
                JOIN formula_item fi ON fi.formula_version_id=p.current_formula_version_id
                JOIN spec_component sc ON sc.specification_version_id=fi.specification_version_id AND sc.match_status='MATCHED'
                WHERE lv.jurisdiction_code='US' AND p.product_id NOT IN ('prod_usda_1106285','prod_usda_1106963')
                  AND NOT EXISTS (SELECT 1 FROM review_task rt WHERE rt.product_id=p.product_id)
                  AND EXISTS (SELECT 1 FROM label_allergen_declaration d WHERE d.label_version_id=lv.label_version_id)
                  AND NOT EXISTS (SELECT 1 FROM label_allergen_declaration d WHERE d.label_version_id=lv.label_version_id AND d.allergen_id='all_milk')
                  AND NOT EXISTS (SELECT 1 FROM ingredient_allergen ia WHERE ia.ingredient_id=sc.ingredient_id AND ia.allergen_id='all_milk' AND ia.rule_set_version_id=lv.rule_set_version_id)
                ORDER BY p.product_id LIMIT 1
                """, String.class);
        var draft = draftWithTask(product);
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        String endpoint = "/api/v1/label-versions/" + label + "/validation-runs";
        String body = JSON.writeValueAsString(Map.of("ruleSetVersionId", rule));
        var first = expect(request("POST", endpoint, body), 201);
        assertThat(first.get("status").stringValue()).isEqualTo("PASSED");
        String oldRun = first.get("validationRunId").stringValue();
        String oldGet = request("GET", "/api/v1/validation-runs/" + oldRun, null).body();
        var oldHistory = jdbc.queryForMap("SELECT * FROM validation_run WHERE validation_run_id=?", oldRun);
        String ingredient = jdbc.queryForObject("""
                SELECT sc.ingredient_id FROM formula_item fi JOIN spec_component sc ON sc.specification_version_id=fi.specification_version_id
                WHERE fi.formula_version_id=? AND sc.match_status='MATCHED' AND NOT EXISTS
                    (SELECT 1 FROM ingredient_allergen ia WHERE ia.ingredient_id=sc.ingredient_id AND ia.allergen_id='all_milk' AND ia.rule_set_version_id=?)
                ORDER BY sc.ingredient_id LIMIT 1
                """, String.class, draft.get("formulaVersionId").stringValue(), rule);
        String mapping = "private_current_mapping_" + UUID.randomUUID().toString().replace("-", "");
        try (var holder = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            try {
                try (var lock = holder.prepareStatement("SELECT product_id FROM product WHERE product_id=? FOR UPDATE")) {
                    lock.setString(1, product); try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                var validation = executor.submit(() -> request("POST", endpoint, body));
                String waitingSql = awaitProductWait();
                assertThat(validation.isDone()).isFalse();
                try (var insert = holder.prepareStatement("""
                        INSERT INTO ingredient_allergen(ingredient_allergen_id,ingredient_id,allergen_id,rule_set_version_id,evidence_rule,data_provenance_id)
                        VALUES (?,?,'all_milk',?,'Private actual mapping committed while validation waits','prov_project_seed')
                        """)) {
                    insert.setString(1, mapping); insert.setString(2, ingredient); insert.setString(3, rule);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                holder.commit();
                var second = expect(validation.get(30, TimeUnit.SECONDS), 201);
                assertThat(second.get("status").stringValue()).isEqualTo("FAILED");
                String newRun = second.get("validationRunId").stringValue();
                assertThat(newRun).isNotEqualTo(oldRun);
                assertThat(second.get("ranAt")).isEqualTo(first.get("ranAt"));
                assertThat(second.has("currentSequence")).isFalse();
                assertThat(second.has("currentValidationRunId")).isFalse();
                assertThat(current(label, rule)).containsEntry("validation_run_id", newRun);
                assertThat(request("GET", "/api/v1/validation-runs/" + oldRun, null).body()).isEqualTo(oldGet);
                assertThat(jdbc.queryForMap("SELECT * FROM validation_run WHERE validation_run_id=?", oldRun)).isEqualTo(oldHistory);
                var rejected = request("POST", "/api/labels/" + label + "/review-submissions", "{}");
                expect(rejected, 409);
                observe("actualValidationFreshMappingAfterProductWait", Map.of("labelVersionId", label,
                        "oldRun", oldRun, "newRun", newRun, "oldStatus", "PASSED", "newStatus", "FAILED",
                        "sameActualRanAt", second.get("ranAt"), "actualBlockedSql", waitingSql,
                        "newMappingId", mapping, "oldGetByteIdentical", true, "validationHttp", 201));
            } finally { holder.rollback(); }
        } finally { jdbc.update("DELETE FROM ingredient_allergen WHERE ingredient_allergen_id=?", mapping); }
    }

    @Test @Timeout(120)
    void aPassingDifferentLabelOrDifferentRuleSetNeverQualifiesTheRequestedTarget() throws Exception {
        var target = draftWithTask(unusedProduct());
        var unrelated = draftWithTask(unusedProduct());
        String label = target.get("labelVersionId").stringValue();
        String rule = target.get("ruleSetVersionId").stringValue();
        insertRun("ffffffff_different_label", unrelated.get("labelVersionId").stringValue(),
                unrelated.get("ruleSetVersionId").stringValue(), "PASSED", "Different label input");
        String alternateRule = jdbc.queryForObject("SELECT rule_set_version_id FROM rule_set_version WHERE rule_set_version_id<>? ORDER BY rule_set_version_id LIMIT 1", String.class, rule);
        insertRun("ffffffff_different_rule", label, alternateRule, "PASSED", "Different rules input");
        var before = allPersistenceRows();
        expect(request("POST", "/api/labels/" + label + "/review-submissions", "{}"), 409);
        assertThat(allPersistenceRows()).isEqualTo(before);
        insertRun("11111111_exact_target", label, rule, "PASSED", "Exact target input");
        var exactPointer = current(label, rule);
        for (String wrongRun : List.of("ffffffff_different_label", "ffffffff_different_rule")) {
            var wrongBinding = catchThrowableOfType(() -> jdbc.update(
                    "UPDATE validation_current_run SET validation_run_id=? WHERE label_version_id=? AND rule_set_version_id=?",
                    wrongRun, label, rule), org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(wrongBinding).isNotNull();
            assertThat(wrongBinding.getMostSpecificCause()).isInstanceOf(SQLException.class);
            assertThat(((SQLException) wrongBinding.getMostSpecificCause()).getErrorCode()).isEqualTo(1452);
            assertThat(current(label, rule)).isEqualTo(exactPointer);
        }
        expect(request("POST", "/api/labels/" + label + "/review-submissions", "{}"), 200);
        observe("exactLabelAndRuleSetIsolation", Map.of("labelVersionId", label, "ruleSetVersionId", rule,
                "unrelatedLabel", unrelated.get("labelVersionId").stringValue(), "differentRuleSet", alternateRule,
                "missingExactTargetRejected", true, "exactTargetAccepted", true, "wrongLabelAndRuleSetPointersRejectedByRealFk", true));
    }

    private void verifyDirection(String product, String oldStatus, String newStatus,
                                 int correctStatus, int baselineStatus) throws Exception {
        var draft = draftWithTask(product);
        String label = draft.get("labelVersionId").stringValue();
        String rule = draft.get("ruleSetVersionId").stringValue();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String older = "ffffffff-ffff-4fff-8fff-" + suffix.substring(0, 12);
        String later = "11111111-1111-4111-8111-" + suffix.substring(0, 12);
        assertThat(older.compareTo(later)).isPositive();
        insertRun(older, label, rule, oldStatus, "First committed fixture run");
        var firstCommitted = jdbc.queryForMap("SELECT * FROM validation_run WHERE validation_run_id=?", older);
        insertRun(later, label, rule, newStatus, "Second committed fixture run");
        var secondCommitted = jdbc.queryForMap("SELECT * FROM validation_run WHERE validation_run_id=?", later);
        assertThat(firstCommitted.get("ran_at")).isEqualTo(secondCommitted.get("ran_at"));
        var historicalRuns = jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY validation_run_id", label);
        var before = allPersistenceRows();
        var response = request("POST", "/api/labels/" + label + "/review-submissions", "{}");
        var observation = new LinkedHashMap<String, Object>();
        observation.put("labelVersionId", label);
        observation.put("ruleSetVersionId", rule);
        observation.put("olderFirstCommitted", firstCommitted);
        observation.put("laterSecondCommitted", secondCommitted);
        observation.put("correctHttpStatus", correctStatus);
        observation.put("observedHttpStatus", response.statusCode());
        observation.put("actualHttpBody", JSON.readTree(response.body()));
        observation.put("actualLabelStatus", jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id=?", String.class, label));
        observation.put("current", current(label, rule));
        assertThat(current(label, rule)).containsEntry("validation_run_id", later)
                .containsEntry("origin", "RECORDED");
        assertThat(((Number) current(label, rule).get("current_sequence")).longValue()).isEqualTo(2L);
        assertThat(((Number) current(label, rule).get("observed_run_count")).longValue()).isEqualTo(2L);
        observations.add(observation);
        assertThat(response.statusCode()).as("actual HTTP body %s", response.body())
                .isEqualTo(Boolean.getBoolean("validation.order.expectBaseline") ? baselineStatus : correctStatus);
        assertThat(jdbc.queryForList("SELECT * FROM validation_run WHERE label_version_id=? ORDER BY validation_run_id", label))
                .as("submission never mutates historical runs").isEqualTo(historicalRuns);
        if (response.statusCode() == 409) assertThat(allPersistenceRows()).isEqualTo(before);
    }

    private void insertRun(String id, String label, String rule, String status, String summary) {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        transaction.executeWithoutResult(ignored -> jdbc.update("CALL sp_insert_validation_run(?,?,?,?,?,?,?,?)",
                id, label, rule, status, S3CompoundMakerFixture.USER_ID, SAME_SECOND, summary,
                jdbc.queryForObject("SELECT data_provenance_id FROM label_version WHERE label_version_id=?", String.class, label)));
    }

    private int bareInsertRun(String id, String label, String rule, String status) {
        String provenance = jdbc.queryForObject("SELECT data_provenance_id FROM label_version WHERE label_version_id=?", String.class, label);
        return jdbc.update("""
                INSERT INTO validation_run(validation_run_id,label_version_id,rule_set_version_id,status,ran_by_user_id,ran_at,summary,data_provenance_id)
                VALUES (?,?,?,?,?,CAST(? AS DATETIME),'Deliberate unsupported bare SQL input',?)
                """, id, label, rule, status, S3CompoundMakerFixture.USER_ID, SAME_SECOND, provenance);
    }

    private JsonNode draftWithTask(String product) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String change = "order_change_" + suffix;
        String impact = "order_impact_" + suffix;
        String finding = "order_finding_" + suffix;
        String task = "order_task_" + suffix;
        jdbc.update("""
                INSERT INTO change_request(change_request_id,change_request_code,change_type,status,
                    requested_at,requested_by_user_id,description,from_formula_version_id,to_formula_version_id,data_provenance_id)
                SELECT ?,?,'FORMULA','ANALYZED',NOW(),'user_label_officer','Private validation ordering task input',
                    current_formula_version_id,current_formula_version_id,data_provenance_id FROM product WHERE product_id=?
                """, change, change, product);
        jdbc.update("""
                INSERT INTO impact_analysis_run(impact_analysis_run_id,run_code,change_request_id,idempotency_key,
                    rule_set_version_id,status,started_at,completed_at,executed_by_user_id,data_provenance_id)
                SELECT ?,?,?,?,lv.rule_set_version_id,'COMPLETED',NOW(),NOW(),'user_label_officer',p.data_provenance_id
                FROM product p JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id WHERE p.product_id=?
                """, impact, impact, change, "impact-analysis:" + change, product);
        jdbc.update("""
                INSERT INTO impact_finding(impact_finding_id,impact_analysis_run_id,product_id,current_formula_version_id,
                    proposed_formula_version_id,current_label_version_id,classification,missing_allergen_codes,explanation,data_provenance_id)
                SELECT ?,?,product_id,current_formula_version_id,current_formula_version_id,current_published_label_version_id,
                    'REVIEW_REQUIRED',JSON_ARRAY(),'Private validation ordering input',data_provenance_id FROM product WHERE product_id=?
                """, finding, impact, product);
        jdbc.update("""
                INSERT INTO review_task(review_task_id,impact_finding_id,product_id,current_label_version_id,
                    draft_label_version_id,target_label_version_id,status,assigned_to_user_id,created_by_user_id,created_at,data_provenance_id)
                SELECT ?,?,product_id,current_published_label_version_id,NULL,NULL,'OPEN','user_approver','user_label_officer',NOW(),data_provenance_id
                FROM product WHERE product_id=?
                """, task, finding, product);
        List<String> allergens = jdbc.queryForList("SELECT allergen_id FROM label_allergen_declaration WHERE label_version_id="
                + "(SELECT current_published_label_version_id FROM product WHERE product_id=?) ORDER BY allergen_id", String.class, product);
        assertThat(allergens).isNotEmpty();
        String body = JSON.writeValueAsString(Map.of("productId", product, "jurisdictionCode", "US", "reviewTaskId", task,
                "declarations", allergens.stream().map(id -> Map.of("allergenId", id, "declarationType", "CONTAINS", "displayText", "Ordering " + id)).toList()));
        var response = request("POST", "/api/labels/drafts", body);
        assertThat(response.statusCode()).as("real draft HTTP body %s", response.body()).isEqualTo(201);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String endpoint, String body) throws Exception {
        return requestAs(method, endpoint, body, MAKER);
    }

    private HttpResponse<String> requestAs(String method, String endpoint, String body, String subject) throws Exception {
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                    .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json")
                    .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", subject)
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private Map<String, Object> current(String label, String rule) {
        return jdbc.queryForMap("SELECT * FROM validation_current_run WHERE label_version_id=? AND rule_set_version_id=?", label, rule);
    }

    private Map<String, List<Map<String, Object>>> allPersistenceRows() {
        var result = new LinkedHashMap<>(S3CompoundMakerFixture.allRows(jdbc));
        for (String table : jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name LIKE 'validation\\_%'
                ORDER BY table_name
                """, String.class)) {
            assertThat(table).matches("[a-z0-9_]+");
            if (!result.containsKey(table)) result.put(table, jdbc.queryForList("SELECT * FROM `" + table + "` ORDER BY 1,2"));
        }
        assertThat(result).as("original 19 business/identity tables plus current and registration").hasSize(21);
        return result;
    }

    private String unusedProduct() {
        return jdbc.queryForObject("""
                SELECT p.product_id FROM product p JOIN label_version lv ON lv.label_version_id=p.current_published_label_version_id
                WHERE lv.jurisdiction_code='US' AND p.product_id NOT IN ('prod_usda_1106285','prod_usda_1106963')
                  AND NOT EXISTS (SELECT 1 FROM review_task rt WHERE rt.product_id=p.product_id)
                  AND EXISTS (SELECT 1 FROM label_allergen_declaration d WHERE d.label_version_id=lv.label_version_id)
                ORDER BY p.product_id LIMIT 1
                """, String.class);
    }

    private void insertRun(Connection connection, String id, String label, String rule, String status) throws SQLException {
        assertThat(connection.getAutoCommit()).as("canonical writer is inside the caller's real transaction").isFalse();
        String provenance = jdbc.queryForObject("SELECT data_provenance_id FROM label_version WHERE label_version_id=?", String.class, label);
        try (var insert = connection.prepareCall("{call sp_insert_validation_run(?,?,?,?,?,?,?,?)}")) {
            insert.setString(1, id); insert.setString(2, label); insert.setString(3, rule); insert.setString(4, status);
            insert.setString(5, S3CompoundMakerFixture.USER_ID); insert.setString(6, SAME_SECOND);
            insert.setString(7, "Uncommitted actual transaction input"); insert.setString(8, provenance);
            insert.execute();
        }
    }

    private String awaitProductWait() throws InterruptedException {
        return awaitDatabaseWait("product", null);
    }

    private String awaitDatabaseWait(String objectName, String statementSubstring) throws InterruptedException {
        // Read lock metadata only from this test's private MySQL instance.
        var lockMetadata = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        List<Map<String, Object>> lastWaits = List.of();
        List<Map<String, Object>> lastProcesses = List.of();
        while (System.nanoTime() < deadline) {
            var productWaits = lockMetadata.queryForList("""
                    SELECT dl.OBJECT_NAME,dl.LOCK_STATUS,dl.LOCK_MODE,dl.LOCK_DATA
                    FROM performance_schema.data_lock_waits dw
                    JOIN performance_schema.data_locks dl ON dl.ENGINE_LOCK_ID=dw.REQUESTING_ENGINE_LOCK_ID
                    WHERE dl.OBJECT_SCHEMA=DATABASE() AND dl.OBJECT_NAME=? AND dl.LOCK_STATUS='WAITING'
                    """, objectName);
            lastWaits = productWaits;
            lastProcesses = jdbc.queryForList("SHOW FULL PROCESSLIST");
            for (var row : lastProcesses) {
                Object info = row.get("Info");
                if (info instanceof String sql) {
                    String normalized = sql.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
                    if (!productWaits.isEmpty() && (statementSubstring != null ? normalized.contains(statementSubstring)
                            : (normalized.contains("from product") && normalized.contains("for update"))
                            || normalized.contains("call sp_submit_label_for_review"))) {
                        return sql + " | actual MySQL data_lock_waits=" + productWaits;
                    }
                }
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        observe("actualLockWaitObservationTimeout", Map.of("expectedObject", objectName,
                "lastActualDataLockWaits", lastWaits, "lastActualProcesslist", lastProcesses));
        throw new AssertionError("Actual lock wait on " + objectName + " was not observed in MySQL data_lock_waits and PROCESSLIST");
    }

    private JsonNode expect(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as("real HTTP %s: %s", response.request().uri(), response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

    private void observe(String name, Map<String, Object> facts) {
        var item = new LinkedHashMap<String, Object>(); item.put("name", name); item.putAll(facts); observations.add(item);
    }
}
