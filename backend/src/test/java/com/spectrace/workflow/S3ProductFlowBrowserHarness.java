package com.spectrace.workflow;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit local acceptance launcher, excluded from default Surefire name patterns.
 * Run with -Dtest=S3ProductFlowBrowserHarness after installing frontend dependencies
 * and a Playwright Chromium browser. It owns its database and both child processes.
 * Production endpoints and frontend execute unchanged; only released specification
 * input is added to the initial Flyway catalog. New declarations and passing runs
 * must be created by the actual browser and HTTP evaluator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectrace.dev-external-auth.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class S3ProductFlowBrowserHarness {
    private static final JsonMapper JSON = JsonMapper.builder().build();
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

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int backendPort;

    @Test
    @Timeout(420)
    void runActualBrowserAgainstIsolatedSpringAndMySql() throws Exception {
        Path checkout = Path.of(System.getProperty("s3.checkout", ".")).toAbsolutePath().normalize();
        if (!Files.isDirectory(checkout.resolve("frontend"))) checkout = checkout.getParent();
        Path frontend = checkout.resolve("frontend");
        assertThat(Files.isRegularFile(frontend.resolve("node_modules/vite/bin/vite.js")))
                .as("frontend npm dependencies must already be installed").isTrue();
        Path evidence = Path.of(System.getProperty("s3.browser.evidence",
                checkout.resolve("test-artifacts/sprint3/live-s3").toString())).toAbsolutePath().normalize();
        Files.createDirectories(evidence);
        S3CompoundMakerFixture.install(jdbc);
        var identityFixtureRows = S3CompoundMakerFixture.rows(jdbc, S3CompoundMakerFixture.IDENTITY_TABLES);
        try (var fixture = Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/s3-compound-maker.sql"))) {
            Files.write(evidence.resolve("isolated-compound-maker-fixture.sql"), fixture.readAllBytes());
        }
        var historicalContent = historicalLabelContent();
        var historicalDeclarations = historicalDeclarations();
        assertThat(historicalContent).hasSize(60);
        String inputOnly;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/fixtures/s3-soy-spec-v2-adoption.sql"))) {
            String fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int boundary = fixture.indexOf("INSERT INTO formula_version");
            assertThat(boundary).isPositive();
            inputOnly = fixture.substring(0, boundary);
            assertThat(inputOnly).doesNotContain("validation_run", "label_version", "review_task");
        }
        new ResourceDatabasePopulator(new ByteArrayResource(inputOnly.getBytes(StandardCharsets.UTF_8)))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        Files.writeString(evidence.resolve("released-spec-input-only.sql"), inputOnly, StandardCharsets.UTF_8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ingredient_specification_version "
                + "WHERE specification_version_id='spec_chocolate_v2'", Integer.class)).isEqualTo(1);
        int frontendPort;
        try (var freePort = new ServerSocket(0)) { frontendPort = freePort.getLocalPort(); }
        var viteCommand = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1",
                "--port", Integer.toString(frontendPort), "--strictPort").directory(frontend.toFile())
                .redirectErrorStream(true).redirectOutput(evidence.resolve("vite.log").toFile());
        viteCommand.environment().put("VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        Process vite = viteCommand.start();
        Process playwright = null;
        try {
            awaitOwnedFrontend(vite, frontendPort);
            // Distinct ordered processes protect the old published-label PASS.
            // Freeze the complete S3 physical proof before the legacy formula
            // lifecycle case deliberately changes one current formula pointer.
            for (String spec : new String[]{"catalog-live", "validation-live", "s3-product-flow-live",
                    "formula-lifecycle-fullstack"}) {
                var arguments = new ArrayList<>(List.of("node", "node_modules/@playwright/test/cli.js", "test",
                        "tests/" + spec + ".spec.ts", "--output",
                        evidence.resolve(spec + "-results").toString(), "--reporter=list"));
                if (spec.equals("catalog-live")) {
                    arguments.add("--grep");
                    arguments.add("M1 real read-only product and formula integration");
                }
                var browserCommand = new ProcessBuilder(arguments)
                        .directory(frontend.toFile()).redirectErrorStream(true)
                        .redirectOutput(evidence.resolve(spec + ".log").toFile());
                browserCommand.environment().put("PLAYWRIGHT_BASE_URL", "http://127.0.0.1:" + frontendPort);
                browserCommand.environment().put("LIVE_VALIDATION", "1");
                browserCommand.environment().put("LIVE_S3_FLOW", "1");
                browserCommand.environment().put("LIVE_CATALOG", "1");
                browserCommand.environment().put("LIVE_WRITES", "1");
                browserCommand.environment().put("S3_COMPOUND_MAKER_SUBJECT", S3CompoundMakerFixture.SUBJECT);
                browserCommand.environment().put("S3_COMPOUND_MAKER_ALIAS_SUBJECT", S3CompoundMakerFixture.CASE_ALIAS_SUBJECT);
                browserCommand.environment().put("S3_COMPOUND_MAKER_USER_ID", S3CompoundMakerFixture.USER_ID);
                browserCommand.environment().put("S3_EVIDENCE_DIR", evidence.toString());
                playwright = browserCommand.start();
                assertThat(playwright.waitFor(360, TimeUnit.SECONDS)).as(spec + " browser process completion").isTrue();
                assertThat(playwright.exitValue()).as("real %s browser output: %s", spec,
                        Files.readString(evidence.resolve(spec + ".log"), StandardCharsets.UTF_8)).isZero();
                if (spec.equals("s3-product-flow-live")) {
                    assertS3PublicationProof();
                    assertThat(historicalLabelContent()).as("all 60 initial label contents remain immutable")
                            .isEqualTo(historicalContent);
                    assertThat(historicalDeclarations()).as("all historical declaration rows remain immutable")
                            .isEqualTo(historicalDeclarations);
                    assertThat(S3CompoundMakerFixture.rows(jdbc, S3CompoundMakerFixture.IDENTITY_TABLES))
                            .as("business commands never alter fixture or existing identity/permission rows")
                            .isEqualTo(identityFixtureRows);
                    var proof = new LinkedHashMap<String, Object>();
                    proof.put("scope", "After S3 compound-maker browser; before the separate formula lifecycle browser");
                    proof.put("closedApprovedTasks", 20);
                    proof.put("independentQaApprovals", 20);
                    proof.put("publications", 20);
                    proof.put("compoundCreatorPublications", 1);
                    proof.put("officerCreatorPublications", 19);
                    proof.put("historicalLabelContentsUnchanged", historicalLabelContent());
                    proof.put("historicalDeclarationsUnchanged", historicalDeclarations());
                    proof.put("physicalRows", S3CompoundMakerFixture.allRows(jdbc));
                    Files.writeString(evidence.resolve("s3-physical-proof-before-formula.json"),
                            JSON.writeValueAsString(proof), StandardCharsets.UTF_8);
                }
            }
            assertThat(Files.isRegularFile(evidence.resolve("s3-product-flow-observations.json"))).isTrue();
            assertThat(Files.isRegularFile(evidence.resolve("s3-physical-proof-before-formula.json"))).isTrue();
            assertThat(S3CompoundMakerFixture.rows(jdbc, S3CompoundMakerFixture.IDENTITY_TABLES)).isEqualTo(identityFixtureRows);
            Files.writeString(evidence.resolve("post-formula-physical-snapshot.json"), JSON.writeValueAsString(Map.of(
                    "scope", "After separate legacy formula lifecycle; current formula pointers intentionally differ from frozen S3 proof",
                    "physicalRows", S3CompoundMakerFixture.allRows(jdbc))), StandardCharsets.UTF_8);
        } finally {
            stopOnlyOwnedProcess(playwright);
            stopOnlyOwnedProcess(vite);
        }
    }

    private void assertS3PublicationProof() {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_task", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_task WHERE status='CLOSED' "
                    + "AND decision='APPROVE'", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record ar JOIN label_version lv "
                    + "ON lv.label_version_id=ar.label_version_id WHERE ar.decision='APPROVE' "
                    + "AND ar.decided_by_user_id='user_approver' "
                    + "AND ar.decided_by_user_id<>lv.created_by_user_id", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record pr JOIN label_version lv "
                    + "ON lv.label_version_id=pr.label_version_id WHERE lv.created_by_user_id='user_label_officer'",
                    Integer.class)).isEqualTo(19);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record pr JOIN label_version lv "
                    + "ON lv.label_version_id=pr.label_version_id WHERE lv.created_by_user_id=?",
                    Integer.class, S3CompoundMakerFixture.USER_ID)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE decided_by_user_id=?",
                    Integer.class, S3CompoundMakerFixture.USER_ID)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publication_record "
                    + "WHERE published_by_user_id='user_publisher'", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_type='LABEL_PUBLISHED'",
                    Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run vr JOIN publication_record pr "
                    + "ON pr.label_version_id=vr.label_version_id WHERE vr.status='PASSED'", Integer.class)).isEqualTo(20);
    }

    private List<Map<String, Object>> historicalLabelContent() {
        return jdbc.queryForList("SELECT label_version_id,product_id,formula_version_id,rule_set_version_id,"
                + "jurisdiction_code,version_number,raw_ingredient_text,created_by_user_id,created_at,data_provenance_id "
                + "FROM label_version WHERE version_number=1 ORDER BY label_version_id");
    }

    private List<Map<String, Object>> historicalDeclarations() {
        return jdbc.queryForList("SELECT lad.* FROM label_allergen_declaration lad JOIN label_version lv "
                + "ON lv.label_version_id=lad.label_version_id WHERE lv.version_number=1 "
                + "ORDER BY lad.label_allergen_declaration_id");
    }

    private void awaitOwnedFrontend(Process vite, int frontendPort) throws Exception {
        // Probe the local development server with HTTP/1.1; readiness still
        // requires a real successful GET, rather than trusting process output.
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                assertThat(vite.isAlive()).as("owned Vite process remains alive").isTrue();
                try {
                    var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + frontendPort))
                            .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() == 200) return;
                } catch (java.io.IOException ignored) { /* the owned server is still starting */ }
                Thread.sleep(200);
            }
            throw new AssertionError("Owned Vite server did not become ready within 30 seconds");
        }
    }

    private static void stopOnlyOwnedProcess(Process process) throws InterruptedException {
        if (process == null) return;
        process.descendants().forEach(child -> { if (child.isAlive()) child.destroy(); });
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.descendants().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
