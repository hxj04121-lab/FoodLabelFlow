package com.spectrace.impact;

import com.spectrace.impact.infrastructure.JdbcChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * A response-level SCRUM-79 regression over real HTTP, authentication and MySQL persistence.
 * The losing request's real identity SELECTs establish its REPEATABLE READ snapshot before
 * it waits on the winner's CR lock. The only spy schedules the real CR repository method;
 * discovery, classification, persistence, authentication and response mapping remain real.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spectrace.dev-external-auth.enabled=true",
                "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ",
                "spring.jdbc.template.query-timeout=30s"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/fixtures/s3-soy-spec-v2-adoption.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ImpactAnalysisConcurrentReplayMySqlTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CR = "cr-scrum79-replay-snapshot";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String TRIGGER = "/api/v1/change-requests/" + CR + "/impact-analyses";
    private static final String PARTICIPANT_HEADER = "X-Test-Replay-Participant";

    // The committed adoption fixture changes the seeded products, so this class owns its DB
    // just like ImpactAnalysisApiMySqlTest rather than mutating the shared support container.
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("scrum79_replay")
            .withPassword("scrum79_replay_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private JdbcChangeRequestRepository changeRequests;

    @BeforeEach
    void insertCommittedSubmittedChangeRequest() {
        jdbc.update("DELETE FROM review_task");
        jdbc.update("DELETE FROM impact_finding");
        jdbc.update("DELETE FROM impact_analysis_run");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM change_request");
        jdbc.update("""
                INSERT INTO change_request(change_request_id, change_request_code, change_type, status,
                    requested_at, requested_by_user_id, description, from_specification_version_id,
                    to_specification_version_id, data_provenance_id)
                VALUES (?, ?, 'INGREDIENT_SPEC', 'SUBMITTED', '2026-10-01 09:00:00', 'user_change_manager',
                    'Concurrent replay snapshot regression', 'spec_chocolate_v1', 'spec_chocolate_v2',
                    'prov_scenario_input')
                """, CR, "CR-" + CR);
    }

    @Test
    void concurrentReplayWithAnAuthenticatedOlderSnapshotReturnsTheCompleteCommittedResource() throws Exception {
        var loserAuthenticated = new CountDownLatch(1);
        var allowLoserLock = new CountDownLatch(1);
        var winnerHasLock = new CountDownLatch(1);
        var allowWinnerToFinish = new CountDownLatch(1);
        var loserTransaction = new AtomicReference<TransactionObservation>();
        var winnerTransaction = new AtomicReference<TransactionObservation>();

        JdbcChangeRequestRepository target = AopTestUtils.getUltimateTargetObject(changeRequests);
        doAnswer(invocation -> {
            var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String participant = request.getHeader(PARTICIPANT_HEADER);
            if ("loser".equals(participant)) {
                // run() has already completed real requireActor(RUN_IMPACT), including the
                // ordinary user_account/role/permission SELECTs on this transaction's connection.
                // Do not add a test SELECT against a data table to manufacture the snapshot.
                loserTransaction.set(observeTransaction());
                loserAuthenticated.countDown();
                await(allowLoserLock, "allow losing request to attempt its real CR lock");
                return invocation.callRealMethod();
            }
            Object result = invocation.callRealMethod();
            if ("winner".equals(participant)) {
                // The actual SELECT ... FOR UPDATE has returned, so this HTTP transaction
                // now owns the CR lock while the loser is released into its actual JDBC call.
                winnerTransaction.set(observeTransaction());
                winnerHasLock.countDown();
                await(allowWinnerToFinish, "allow winning request to finish and commit");
            }
            return result;
        }).when(target).lockById(eq(CR));

        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            try {
                CompletableFuture<HttpResponse<String>> loser = trigger(client, "loser");
                await(loserAuthenticated, "real authentication completed before the losing CR lock");
                CompletableFuture<HttpResponse<String>> winner = trigger(client, "winner");
                await(winnerHasLock, "winning request acquired the actual CR row lock");
                assertRepeatableRead(loserTransaction.get());
                assertRepeatableRead(winnerTransaction.get());
                assertThat(loserTransaction.get().connectionId()).isNotEqualTo(winnerTransaction.get().connectionId());

                allowLoserLock.countDown();
                awaitActualCrLockWait(loserTransaction.get().connectionId(), winnerTransaction.get().connectionId());
                allowWinnerToFinish.countDown();

                HttpResponse<String> created = winner.get(20, TimeUnit.SECONDS);
                HttpResponse<String> replay = loser.get(20, TimeUnit.SECONDS);
                assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
                assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
                JsonNode createdBody = JSON.readTree(created.body());
                JsonNode replayBody = JSON.readTree(replay.body());
                String analysisId = createdBody.get("impactAnalysisId").stringValue();
                assertThat(created.headers().firstValue("Location"))
                        .contains("/api/v1/impact-analyses/" + analysisId);

                HttpResponse<String> read = client.send(request("GET", "/api/v1/impact-analyses/" + analysisId,
                        null, "dev-external-auditor", null), HttpResponse.BodyHandlers.ofString());
                assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
                JsonNode freshBody = JSON.readTree(read.body());
                assertCompleteResource(createdBody);
                assertCompleteResource(freshBody);
                assertThat(freshBody).as("fresh committed GET equals the complete winner response")
                        .isEqualTo(createdBody);
                assertThat(replayBody.get("impactAnalysisId").stringValue()).isEqualTo(analysisId);
                assertThat(rowCounts()).isEqualTo(Map.of("runs", 1L, "findings", 40L, "tasks", 20L, "audits", 1L));
                assertThat(jdbc.queryForObject("SELECT status FROM change_request WHERE change_request_id = ?",
                        String.class, CR)).isEqualTo("ANALYZED");
                System.out.println("CONCURRENT_REPLAY_EVIDENCE: " + JSON.writeValueAsString(Map.ofEntries(
                        Map.entry("winnerStatus", created.statusCode()), Map.entry("replayStatus", replay.statusCode()),
                        Map.entry("freshGetStatus", read.statusCode()), Map.entry("analysisId", analysisId),
                        Map.entry("sameAnalysisId", true), Map.entry("winnerAndGetEqual", true),
                        Map.entry("winnerFindings", createdBody.get("findings").size()),
                        Map.entry("freshGetFindings", freshBody.get("findings").size()),
                        Map.entry("replayFindings", replayBody.get("findings").size()),
                        Map.entry("replayRelevantProductCount", replayBody.get("relevantProductCount").intValue()),
                        Map.entry("replayNoActionCount", replayBody.get("noActionCount").intValue()),
                        Map.entry("replayReviewRequiredCount", replayBody.get("reviewRequiredCount").intValue()),
                        Map.entry("mysqlIsolation", loserTransaction.get().mysqlIsolation()),
                        Map.entry("loserConnectionId", loserTransaction.get().connectionId()),
                        Map.entry("winnerConnectionId", winnerTransaction.get().connectionId()),
                        Map.entry("actualCrLockWaitObserved", true), Map.entry("databaseCounts", rowCounts()))));
                assertCompleteResource(replayBody);
                assertThat(replayBody).as("200 replay contains every field and identifier from the 201 winner")
                        .isEqualTo(createdBody);
                assertThat(replayBody).as("200 replay agrees with a fresh committed GET")
                        .isEqualTo(freshBody);
            } finally {
                // Also release both server threads if coordination or an assertion fails.
                allowLoserLock.countDown();
                allowWinnerToFinish.countDown();
            }
        }
    }

    private TransactionObservation observeTransaction() {
        return jdbc.execute((ConnectionCallback<TransactionObservation>) connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT CONNECTION_ID(), @@transaction_isolation")) {
                result.next();
                return new TransactionObservation(result.getLong(1), connection.getTransactionIsolation(),
                        result.getString(2), connection.getAutoCommit(),
                        TransactionSynchronizationManager.isActualTransactionActive());
            }
        });
    }

    private static void assertRepeatableRead(TransactionObservation transaction) {
        assertThat(transaction).isNotNull();
        assertThat(transaction.connectionId()).isPositive();
        assertThat(transaction.isolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(transaction.mysqlIsolation()).isEqualTo("REPEATABLE-READ");
        assertThat(transaction.autoCommit()).isFalse();
        assertThat(transaction.springTransactionActive()).isTrue();
    }

    private static void awaitActualCrLockWait(long loserConnectionId, long winnerConnectionId) throws Exception {
        // The observer is independent of both application transactions. Root is used only
        // for MySQL lock metadata; no application privilege or persistence behavior changes.
        var observerProperties = new Properties();
        observerProperties.setProperty("user", "root");
        observerProperties.setProperty("password", MYSQL.getPassword());
        try (var observer = DriverManager.getConnection(MYSQL.getJdbcUrl(), observerProperties);
             var statement = observer.prepareStatement("""
                     SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                     JOIN performance_schema.threads requester ON requester.THREAD_ID = w.REQUESTING_THREAD_ID
                     JOIN performance_schema.threads blocker ON blocker.THREAD_ID = w.BLOCKING_THREAD_ID
                     JOIN performance_schema.data_locks requested
                       ON requested.ENGINE = w.ENGINE AND requested.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
                     WHERE requester.PROCESSLIST_ID = ? AND blocker.PROCESSLIST_ID = ?
                       AND requested.OBJECT_SCHEMA = 'spectrace' AND requested.OBJECT_NAME = 'change_request'
                       AND requested.LOCK_TYPE = 'RECORD' AND requested.LOCK_STATUS = 'WAITING'
                     """)) {
            statement.setLong(1, loserConnectionId);
            statement.setLong(2, winnerConnectionId);
            statement.setQueryTimeout(2);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (result.getLong(1) > 0) return;
                }
                Thread.sleep(25);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("MySQL did not show loser connection " + loserConnectionId
                    + " waiting for winner connection " + winnerConnectionId + " on the change_request row lock");
        }
    }

    private static void assertCompleteResource(JsonNode body) {
        assertThat(body.get("impactAnalysisId").stringValue()).isNotBlank();
        assertThat(body.get("changeRequestId").stringValue()).isEqualTo(CR);
        assertThat(body.get("ruleSetVersionId").stringValue()).isEqualTo(RULE_SET);
        assertThat(body.get("status").stringValue()).isEqualTo("COMPLETED");
        assertThat(body.get("completedAt").stringValue()).endsWith("Z");
        assertThat(body.get("relevantProductCount").intValue()).as("complete impact response: %s", body).isEqualTo(40);
        assertThat(body.get("noActionCount").intValue()).isEqualTo(20);
        assertThat(body.get("reviewRequiredCount").intValue()).isEqualTo(20);
        assertThat(body.get("findings").size()).isEqualTo(40);
        var productIds = new ArrayList<String>();
        var findingIds = new HashSet<String>();
        var taskIds = new HashSet<String>();
        int noAction = 0;
        int reviewRequired = 0;
        for (JsonNode finding : body.get("findings")) {
            productIds.add(finding.get("productId").stringValue());
            String findingId = finding.get("impactFindingId").stringValue();
            assertThat(findingId).isNotBlank();
            assertThat(findingIds.add(findingId)).isTrue();
            assertThat(finding.get("explanation").stringValue()).isNotBlank();
            assertThat(finding.get("proposedFormulaVersionId").stringValue())
                    .isEqualTo(finding.get("currentFormulaVersionId").stringValue() + "n1soy");
            if ("NO_ACTION".equals(finding.get("outcome").stringValue())) {
                noAction++;
                assertThat(finding.get("missingAllergenCodes").size()).isZero();
                assertThat(finding.get("reviewTask")).isNull();
            } else {
                reviewRequired++;
                assertThat(finding.get("outcome").stringValue()).isEqualTo("REVIEW_REQUIRED");
                assertThat(finding.get("missingAllergenCodes").size()).isEqualTo(1);
                assertThat(finding.get("missingAllergenCodes").get(0).stringValue()).isEqualTo("SOY");
                JsonNode task = finding.get("reviewTask");
                assertThat(task).isNotNull();
                String taskId = task.get("reviewTaskId").stringValue();
                assertThat(taskId).isNotBlank();
                assertThat(taskIds.add(taskId)).isTrue();
                for (String reference : new String[]{"impactFindingId", "productId", "currentFormulaVersionId", "currentLabelVersionId"}) {
                    assertThat(task.get(reference)).as("reviewTask.%s references its finding", reference)
                            .isEqualTo(finding.get(reference));
                }
                assertThat(task.get("draftLabelVersionId")).isNotNull();
                assertThat(task.get("draftLabelVersionId").isNull()).isTrue();
            }
        }
        assertThat(productIds).isSorted().doesNotHaveDuplicates();
        assertThat(noAction).isEqualTo(20);
        assertThat(reviewRequired).isEqualTo(20);
        assertThat(taskIds).hasSize(20);
    }

    private Map<String, Long> rowCounts() {
        return Map.of("runs", count("impact_analysis_run"), "findings", count("impact_finding"),
                "tasks", count("review_task"), "audits", count("audit_event"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private CompletableFuture<HttpResponse<String>> trigger(HttpClient client, String participant) {
        return client.sendAsync(request("POST", TRIGGER, "{\"ruleSetVersionId\":\"" + RULE_SET + "\"}",
                "dev-external-change-manager", participant), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String body, String subject, String participant) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json")
                .header("Accept", "application/json").header("X-Auth-Provider", "DEV_EXTERNAL")
                .header("X-External-Subject", subject);
        if (participant != null) builder.header(PARTICIPANT_HEADER, participant);
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static void await(CountDownLatch latch, String description) throws InterruptedException {
        assertThat(latch.await(20, TimeUnit.SECONDS)).as(description).isTrue();
    }

    private record TransactionObservation(long connectionId, int isolation, String mysqlIsolation, boolean autoCommit,
                                          boolean springTransactionActive) { }
}
