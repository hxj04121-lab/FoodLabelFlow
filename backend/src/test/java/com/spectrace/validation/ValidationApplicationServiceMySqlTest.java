package com.spectrace.validation;

import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import com.spectrace.validation.infrastructure.JdbcValidationResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
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

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** No test-managed transaction: every assertion observes a service commit or rollback. */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.target=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = PositiveGoldenFixtures.SQL_RESOURCE, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = {
        "INSERT INTO `role`(role_id, role_code, role_name) VALUES ('role_scrum44', 'SCRUM44', 'Validation fixture')",
        "INSERT INTO permission(permission_id, permission_code, permission_name) "
                + "VALUES ('permission_scrum44', 'LABEL.VALIDATE', 'Validate labels')",
        "INSERT INTO user_role(user_id, role_id, assigned_at) "
                + "VALUES ('user_s2_m2_fixture', 'role_scrum44', UTC_TIMESTAMP())",
        "INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum44', 'permission_scrum44')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ValidationApplicationServiceMySqlTest {
    private static final String LABEL = "label_s2_m2_multi_v1";
    private static final String FORMULA = "formula_s2_m2_multi_v1";
    private static final String RULE_SET = PositiveGoldenFixtures.RULE_SET_VERSION_ID;
    private static final String ACTOR = "user_s2_m2_fixture";
    private static final String SUBJECT = "s2-m2-positive-fixture";
    private static final String PROVENANCE = "prov_s2_m2_positive_v1";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace")
            .withUsername("spectrace_fixture")
            .withPassword("spectrace_fixture_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private ValidationApplicationService service;
    @Autowired
    private ValidationRunRepository runs;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @MockitoSpyBean
    private JdbcValidationResultRepository results;
    @MockitoSpyBean
    private AuditApplicationService audit;
    private AuditApplicationService auditTarget;

    @BeforeEach
    void resetIsolatedFixtureAndAuthenticate() {
        // Stub the spy behind the proxy; production calls still traverse MANDATORY advice.
        auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM validation_result");
        jdbc.update("DELETE FROM validation_run");
        jdbc.update("UPDATE label_version SET lifecycle_status = 'DRAFT', is_current_published = 'N' "
                + "WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE formula_version SET is_current_released = 'Y' WHERE formula_version_id = ?", FORMULA);
        jdbc.update("UPDATE rule_set_version SET lifecycle_status = 'ACTIVE', jurisdiction_code = 'US', "
                + "effective_from = '2026-01-01', effective_to = NULL WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE rule_definition SET severity = 'INFO', is_active = 'Y' WHERE rule_set_version_id = ?", RULE_SET);
        jdbc.update("UPDATE spec_component SET match_status = 'MATCHED' WHERE spec_component_id = 'component_s2_m2_soy_v1'");
        jdbc.update("UPDATE user_account SET is_active = 'Y' WHERE user_id = ?", ACTOR);
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum44'");
        jdbc.update("INSERT INTO role_permission(role_id, permission_id) VALUES ('role_scrum44', 'permission_scrum44')");
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        for (var declaration : PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).labelSnapshot().declarations()) {
            jdbc.update("""
                    INSERT INTO label_allergen_declaration(
                      label_allergen_declaration_id, label_version_id, allergen_id,
                      declaration_type, declaration_source, display_text, data_provenance_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, "scrum44-" + declaration.allergenId(), LABEL, declaration.allergenId(),
                    declaration.declarationType(), declaration.declarationSource(), declaration.displayText(), PROVENANCE);
        }
        authenticate(SUBJECT);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void commitsPassedRunEveryFindingAndAttributableAudit() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var run = service.validate(LABEL, RULE_SET);

        assertThat(runs.findById(run.validationRunId())).contains(run);
        assertThat(run.status()).isEqualTo(ValidationStatus.PASSED);
        assertThat(run.labelVersionId()).isEqualTo(LABEL);
        assertThat(run.ruleSetVersionId()).isEqualTo(RULE_SET);
        assertThat(run.ranByUserId()).isEqualTo(ACTOR);
        assertThat(run.dataProvenanceId()).isEqualTo(PROVENANCE);
        assertThat(run.ranAt()).isBetween(before, Instant.now());
        assertThat(run.summary()).isNull();
        var saved = results.findByRunId(run.validationRunId());
        assertThat(saved).hasSize(3).allSatisfy(result -> {
            assertThat(result.validationRunId()).isEqualTo(run.validationRunId());
            assertThat(result.severity()).isEqualTo(ValidationSeverity.INFO);
            assertThat(result.passed()).isTrue();
            assertThat(result.blocking()).isFalse();
        });
        assertThat(saved.stream().map(result -> new ValidationFinding(
                result.ruleDefinitionId(), result.resultCode(), result.severity(),
                result.passed(), result.blocking(), result.message())).toList())
                .containsExactlyInAnyOrderElementsOf(PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID).findings());
        assertThat(saved).extracting(ValidationResult::validationResultId).doesNotHaveDuplicates();
        var event = jdbc.queryForMap("""
                SELECT event_type, entity_type, entity_id, actor_user_id, data_provenance_id,
                       event_payload->>'$.validationRunId' AS run_id,
                       event_payload->>'$.ruleSetVersionId' AS rule_set_id
                FROM audit_event WHERE correlation_id = ?
                """, run.validationRunId());
        assertThat(event).containsEntry("event_type", "LABEL_VALIDATION")
                .containsEntry("entity_type", "LABEL_VERSION")
                .containsEntry("entity_id", LABEL)
                .containsEntry("actor_user_id", ACTOR)
                .containsEntry("data_provenance_id", PROVENANCE)
                .containsEntry("run_id", run.validationRunId())
                .containsEntry("rule_set_id", RULE_SET);
    }

    @Test
    void persistsAllBlockingFailuresAndStillAuditsTheCompletedRun() {
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE rule_definition SET severity = 'ERROR' WHERE rule_set_version_id = ?", RULE_SET);

        var run = service.validate(LABEL, RULE_SET);

        assertThat(run.status()).isEqualTo(ValidationStatus.FAILED);
        assertThat(runs.findById(run.validationRunId())).contains(run);
        assertThat(results.findByRunId(run.validationRunId())).hasSize(3).allSatisfy(result -> {
            assertThat(result.resultCode()).isEqualTo("ALLERGEN_DECLARATION_MISSING");
            assertThat(result.message()).endsWith(" is derived but no CONTAINS declaration is present.");
            assertThat(result.severity()).isEqualTo(ValidationSeverity.ERROR);
            assertThat(result.passed()).isFalse();
            assertThat(result.blocking()).isTrue();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE correlation_id = ?",
                Integer.class, run.validationRunId())).isEqualTo(1);
    }

    @Test
    void retainsNonBlockingFailuresWithoutChangingPassedStatus() {
        jdbc.update("DELETE FROM label_allergen_declaration WHERE label_version_id = ?", LABEL);
        jdbc.update("UPDATE rule_definition SET severity = 'WARNING' WHERE rule_set_version_id = ?", RULE_SET);

        var run = service.validate(LABEL, RULE_SET);

        assertThat(run.status()).isEqualTo(ValidationStatus.PASSED);
        assertThat(results.findByRunId(run.validationRunId())).hasSize(3).allSatisfy(result -> {
            assertThat(result.severity()).isEqualTo(ValidationSeverity.WARNING);
            assertThat(result.passed()).isFalse();
            assertThat(result.blocking()).isFalse();
        });
    }

    @Test
    void preservesInputFindingWithNullRuleDefinitionAlongsideEveryRuleFinding() {
        jdbc.update("UPDATE spec_component SET match_status = 'UNMAPPED' WHERE spec_component_id = 'component_s2_m2_soy_v1'");

        var run = service.validate(LABEL, RULE_SET);

        assertThat(run.status()).isEqualTo(ValidationStatus.FAILED);
        assertThat(results.findByRunId(run.validationRunId())).hasSize(4)
                .filteredOn(result -> result.ruleDefinitionId() == null)
                .singleElement().satisfies(result -> {
                    assertThat(result.resultCode()).isEqualTo("FORMULA_COMPONENT_UNRESOLVED");
                    assertThat(result.severity()).isEqualTo(ValidationSeverity.ERROR);
                    assertThat(result.passed()).isFalse();
                    assertThat(result.blocking()).isTrue();
                });
    }

    @Test
    void repeatedValidationCreatesIndependentRunsAndResultIds() {
        var first = service.validate(LABEL, RULE_SET);
        var second = service.validate(LABEL, RULE_SET);

        assertThat(second.validationRunId()).isNotEqualTo(first.validationRunId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT validation_result_id FROM validation_result", String.class))
                .hasSize(6).doesNotHaveDuplicates();
    }

    @Test
    void auditFailureAfterInsertRollsBackRunAllResultsAndAudit() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            invocation.callRealMethod();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
            throw new IllegalStateException("injected failure after audit insert");
        }).when(auditTarget).recordValidationEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.validate(LABEL, RULE_SET))
                .isInstanceOf(IllegalStateException.class).hasMessage("injected failure after audit insert");

        assertEmptyPersistence();
    }

    @Test
    void resultBatchConstraintFailureRollsBackRunAndAlreadyInsertedResults() {
        doAnswer(invocation -> {
            List<ValidationResult> values = invocation.getArgument(1);
            var writer = new JdbcValidationResultRepository(jdbc);
            writer.saveAll(invocation.getArgument(0), List.of(values.getFirst()));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isEqualTo(1);
            // A real MySQL duplicate-key error after the first result has been inserted.
            writer.saveAll(invocation.getArgument(0), values);
            return null;
        }).when(results).saveAll(anyString(), anyList());

        assertThatThrownBy(() -> service.validate(LABEL, RULE_SET)).isInstanceOf(DataIntegrityViolationException.class);

        assertEmptyPersistence();
        verify(auditTarget, never()).recordValidationEvent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERSEDED", "REJECTED", "PUBLISHED"})
    void rejectsHistoricalLabelsWithoutWritingAnything(String lifecycle) {
        jdbc.update("UPDATE label_version SET lifecycle_status = ? WHERE label_version_id = ?", lifecycle, LABEL);

        assertFailure(LABEL, RULE_SET, "LABEL_VERSION_NOT_CURRENT", 409);
    }

    @Test
    void rejectsLabelWhoseFormulaIsNoLongerCurrent() {
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", FORMULA);

        assertFailure(LABEL, RULE_SET, "LABEL_VERSION_NOT_CURRENT", 409);
    }

    @Test
    void rejectsMissingLabelAndDifferentRequestedRuleSet() {
        assertFailure("missing-label", RULE_SET, "RESOURCE_NOT_FOUND", 404);
        assertFailure(LABEL, "another-rule-set-version", "VALIDATION_PRECONDITION_FAILED", 422);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lifecycle_status = 'DRAFT'", "lifecycle_status = 'RETIRED'",
            "effective_from = '9999-01-01'", "effective_to = '2026-01-02'", "jurisdiction_code = 'EU'"
    })
    void rejectsUnusableExactRuleSet(String fixtureChange) {
        jdbc.update("UPDATE rule_set_version SET " + fixtureChange + " WHERE rule_set_version_id = ?", RULE_SET);

        assertFailure(LABEL, RULE_SET, "VALIDATION_PRECONDITION_FAILED", 422);
    }

    @Test
    void rejectsRuleSetWithoutActiveDefinitions() {
        jdbc.update("UPDATE rule_definition SET is_active = 'N' WHERE rule_set_version_id = ?", RULE_SET);

        assertFailure(LABEL, RULE_SET, "VALIDATION_PRECONDITION_FAILED", 422);
    }

    @Test
    void rejectsUntrustedInactiveAndUnauthorizedActorsBeforeWriting() {
        authenticate("unknown-subject");
        assertThatThrownBy(() -> service.validate(LABEL, RULE_SET)).isInstanceOf(UnknownIdentityException.class);
        assertEmptyPersistence();

        authenticate(SUBJECT);
        jdbc.update("UPDATE user_account SET is_active = 'N' WHERE user_id = ?", ACTOR);
        assertThatThrownBy(() -> service.validate(LABEL, RULE_SET)).isInstanceOf(UnknownIdentityException.class);
        assertEmptyPersistence();

        jdbc.update("UPDATE user_account SET is_active = 'Y' WHERE user_id = ?", ACTOR);
        jdbc.update("DELETE FROM role_permission WHERE role_id = 'role_scrum44'");
        assertThatThrownBy(() -> service.validate(LABEL, RULE_SET)).isInstanceOf(AuthorizationDeniedException.class);
        assertEmptyPersistence();
    }

    @Test
    @Timeout(30)
    void protectsCurrentLabelFormulaRulesAndSnapshotRowsUntilAuditCommits() throws Exception {
        var reachedAudit = new CountDownLatch(1);
        var releaseAudit = new CountDownLatch(1);
        doAnswer(invocation -> {
            reachedAudit.countDown();
            assertThat(releaseAudit.await(15, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(auditTarget).recordValidationEvent(anyString(), anyString(), anyString(), anyString(), anyString());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var validation = executor.submit(() -> {
                authenticate(SUBJECT);
                try {
                    return service.validate(LABEL, RULE_SET);
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
            try {
                assertThat(reachedAudit.await(15, TimeUnit.SECONDS)).isTrue();
                // A second connection sees no partial commit while audit is pending.
                assertEmptyPersistence();
                try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    for (String lockedRow : List.of(
                            "label_version WHERE label_version_id = '" + LABEL + "'",
                            "label_allergen_declaration WHERE label_version_id = '" + LABEL + "'",
                            "formula_version WHERE formula_version_id = '" + FORMULA + "'",
                            "formula_item WHERE formula_version_id = '" + FORMULA + "'",
                            "spec_component WHERE spec_component_id = 'component_s2_m2_soy_v1'",
                            "rule_set_version WHERE rule_set_version_id = '" + RULE_SET + "'",
                            "rule_definition WHERE rule_set_version_id = '" + RULE_SET + "'")) {
                        assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM " + lockedRow + " FOR UPDATE NOWAIT"))
                                .as("snapshot row remains locked: %s", lockedRow)
                                .isInstanceOf(SQLException.class)
                                .satisfies(error -> assertThat(((SQLException) error).getErrorCode()).isEqualTo(3572));
                    }
                    connection.rollback();
                }
            } finally {
                releaseAudit.countDown();
            }
            var run = validation.get(10, TimeUnit.SECONDS);
            assertThat(runs.findById(run.validationRunId())).contains(run);
            assertThat(results.findByRunId(run.validationRunId())).hasSize(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        }
    }

    private void assertFailure(String labelId, String ruleSetId, String code, int httpStatus) {
        assertThatThrownBy(() -> service.validate(labelId, ruleSetId))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo(code);
                    assertThat(failure.status()).isEqualTo(httpStatus);
                });
        assertEmptyPersistence();
    }

    private void assertEmptyPersistence() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_run", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM validation_result", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
    }

    private static void authenticate(String subject) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Provider", "DEV_EXTERNAL");
        request.addHeader("X-External-Subject", subject);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
