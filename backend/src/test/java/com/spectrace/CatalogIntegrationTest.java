package com.spectrace;

import com.spectrace.catalog.application.CatalogIntegration;
import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogCommands.*;
import com.spectrace.catalog.domain.CatalogFailure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CatalogIntegrationTest.Adapters.class)
class CatalogIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace").withUsername("root").withPassword("catalog-test-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired CatalogService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestAdapter adapter;
    @Autowired Environment environment;
    static final String PROVENANCE = "prov_project_seed";

    /** Test-only identity/audit adapter. Never loaded in the deployable application. */
    @TestConfiguration static class Adapters {
        @Bean @Primary TestAdapter catalogAdapter(JdbcTemplate jdbc) { return new TestAdapter(jdbc); }
    }
    static class TestAdapter implements CatalogIntegration {
        private final JdbcTemplate jdbc;
        volatile boolean failAudit;
        volatile boolean deny;
        TestAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public String requireActor(String permission) {
            if (deny) throw new CatalogFailure(403, "AUTHORIZATION_DENIED", "Test actor denied");
            assertThat(permission).isIn("DATA.MAINTAIN", "FORMULA.RELEASE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE user_id='user_admin' AND is_active='Y'", Integer.class)).isEqualTo(1);
            return "user_admin";
        }
        public void audit(String actor, String action, String id, String provenance) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (failAudit) throw new IllegalStateException("simulated audit failure");
            jdbc.update("""
                    INSERT INTO audit_event(audit_event_id,event_type,entity_type,entity_id,event_at,actor_user_id,
                      event_payload,data_provenance_id) VALUES (?,?,'CATALOG',?,UTC_TIMESTAMP(),?,JSON_OBJECT(),?)
                    """, UUID.randomUUID().toString(), action, id, actor, provenance);
        }
    }
    Formula command(String product) {
        return new Formula(product, PROVENANCE, List.of(new Item("mat_chocolate_base", "spec_chocolate_v1", null, null),
                new Item("mat_soy_carrier", "spec_soy_carrier_v1", null, null)));
    }
    String current(String product) { return (String) service.get(PRODUCT, product).get("current_formula_version_id"); }
    String id(Map<String, Object> row, String key) { return (String) row.get(key); }

    @Test void createsSupplierMaterialSpecificationAndTracesAnImmutableReleasedFormula() {
        var supplier = service.create(new Supplier("TEST-" + UUID.randomUUID(), "S1 supplier", PROVENANCE));
        var material = service.create(new Material(id(supplier,"supplier_id"), "ing_cocoa", "COCOA", "Cocoa", null, PROVENANCE));
        var spec = service.create(new Specification(id(material,"supplier_material_id"), LocalDate.of(2020,1,1), PROVENANCE,
                List.of(new Component("ing_cocoa", "cocoa", "manual verified fixture"))));
        String specId = id(spec, "specification_version_id");
        assertThat(spec.get("version_number")).isEqualTo(1);
        var command = new Formula("prod_usda_1106285", PROVENANCE,
                List.of(new Item(id(material,"supplier_material_id"), specId, null, null)));
        assertThatThrownBy(() -> service.create(command)).isInstanceOfSatisfying(CatalogFailure.class,
                e -> assertThat(e.code()).isEqualTo("SPECIFICATION_NOT_RELEASED"));
        service.releaseSpecification(specId);
        String oldId = current(command.productId());
        var old = service.get(FORMULA, oldId);
        var created = service.create(command);
        String newId = id(created,"formula_version_id");
        assertThat(created.get("version_number")).isEqualTo(2);
        assertThat(service.releaseFormula(newId, new Release(oldId)).get("lifecycle_status")).isEqualTo("RELEASED");
        assertThat(current(command.productId())).isEqualTo(newId);
        var history = service.get(FORMULA, oldId);
        assertThat(history.get("items")).isEqualTo(old.get("items"));
        assertThat(history.get("released_at")).isEqualTo(old.get("released_at"));
        assertThat(history.get("is_current_released")).isEqualTo("N");
        assertThat(service.trace(newId).toString()).contains("S1 supplier", specId, "manual verified fixture");
        assertThatThrownBy(() -> service.releaseFormula(newId, new Release(newId)))
                .isInstanceOfSatisfying(CatalogFailure.class, e -> assertThat(e.code()).isEqualTo("VERSION_IMMUTABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id=?", Integer.class, newId)).isEqualTo(2);
    }
    @Test void rejectsMismatchedAndFutureSpecificationsWithoutLeavingDrafts() {
        var product = "prod_usda_1106963";
        int before = service.versions(product).size();
        assertThatThrownBy(() -> service.create(new Formula(product, PROVENANCE,
                List.of(new Item("mat_soy_carrier", "spec_chocolate_v1", null, null)))))
                .isInstanceOfSatisfying(CatalogFailure.class, e -> assertThat(e.code()).isEqualTo("SPECIFICATION_MATERIAL_MISMATCH"));
        var spec = service.create(new Specification("mat_neutral_base", LocalDate.now().plusYears(2), PROVENANCE,
                List.of(new Component("ing_neutral_base", "base", "test"))));
        String id = id(spec,"specification_version_id");
        service.releaseSpecification(id);
        assertThatThrownBy(() -> service.create(new Formula(product, PROVENANCE,
                List.of(new Item("mat_neutral_base", id, null, null)))))
                .isInstanceOfSatisfying(CatalogFailure.class, e -> assertThat(e.code()).isEqualTo("SPECIFICATION_NOT_EFFECTIVE"));
        assertThat(service.versions(product)).hasSize(before);
    }
    @Test void auditFailureRollsBackCreationAndReleaseAtomically() {
        String product = "prod_usda_1107123";
        String old = current(product);
        String id = id(service.create(command(product)), "formula_version_id");
        int count = service.versions(product).size();
        adapter.failAudit = true;
        try {
            assertThatThrownBy(() -> service.releaseFormula(id, new Release(old))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.create(command(product))).isInstanceOf(IllegalStateException.class);
        } finally { adapter.failAudit = false; }
        assertThat(current(product)).isEqualTo(old);
        assertThat(service.get(FORMULA,id).get("lifecycle_status")).isEqualTo("DRAFT");
        assertThat(service.get(FORMULA,old).get("is_current_released")).isEqualTo("Y");
        assertThat(service.versions(product)).hasSize(count);
    }
    @Test void concurrentReleasesHaveOneWinnerAndOneStaleConflict() throws Exception {
        String product = "prod_usda_1106980";
        String old = current(product);
        String a = id(service.create(command(product)),"formula_version_id");
        String b = id(service.create(command(product)),"formula_version_id");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = List.of(a,b).stream().map(id -> pool.submit(() -> {
                start.await();
                try { service.releaseFormula(id, new Release(old)); return "RELEASED"; }
                catch (CatalogFailure e) { return e.code(); }
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("RELEASED", "CURRENT_FORMULA_CHANGED");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formula_version WHERE product_id=? AND is_current_released='Y'", Integer.class, product)).isEqualTo(1);
    }
    @Test void concurrentCreatesAllocateDistinctMonotonicVersions() throws Exception {
        String product = "prod_usda_1108162";
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = List.of(1,2).stream().map(ignored -> pool.submit(() -> {
                start.await();
                return service.create(command(product)).get("version_number");
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(2, 3);
        }
    }
    @Test void httpContractReturnsRealDatabaseDataAndRejectsInvalidAndUnauthorizedWrites() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String base = "http://localhost:" + environment.getProperty("local.server.port") + "/api/catalog";
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/formulas/formula_1106963_v1/trace")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("spec_chocolate_v1", "Demo Chocolate Supplier", "specificationEvidence");
            var invalid = HttpRequest.newBuilder(URI.create(base + "/suppliers")).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"\",\"name\":\"test\",\"provenanceId\":\"prov_project_seed\"}")).build();
            var invalidResponse = client.send(invalid, HttpResponse.BodyHandlers.ofString());
            assertThat(invalidResponse.statusCode()).isEqualTo(400);
            assertThat(invalidResponse.body()).contains(
                    "\"code\":\"INVALID_REQUEST\"", "\"message\":", "\"traceId\":null", "\"evidenceId\":null");
            var create = HttpRequest.newBuilder(URI.create(base + "/suppliers")).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"HTTP-" + UUID.randomUUID() + "\",\"name\":\"HTTP supplier\",\"provenanceId\":\"prov_project_seed\"}")).build();
            adapter.deny = true;
            try {
                var forbidden = client.send(create, HttpResponse.BodyHandlers.ofString());
                assertThat(forbidden.statusCode()).isEqualTo(403);
                assertThat(forbidden.body()).contains(
                        "\"code\":\"AUTHORIZATION_DENIED\"", "\"traceId\":null", "\"evidenceId\":null");
            }
            finally { adapter.deny = false; }
            assertThat(client.send(create,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
            var duplicate = client.send(create, HttpResponse.BodyHandlers.ofString());
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(duplicate.body()).contains(
                    "\"code\":\"DATA_CONFLICT\"", "\"traceId\":null", "\"evidenceId\":null");
        }
    }
}
