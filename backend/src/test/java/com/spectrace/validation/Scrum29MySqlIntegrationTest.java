package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import com.spectrace.validation.infrastructure.RequestAuthorizationAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Scrum29MySqlIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String CURRENT_LABEL = "label_1106285_v1";
    private static final String CURRENT_FORMULA = "formula_1106285_v1";
    private static final String ACTIVE_RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String AUTHORIZED_SUBJECT = "dev-external-label-officer";
    private static final String UNAUTHORIZED_SUBJECT = "dev-external-auditor";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LabelSnapshotPort labelSnapshots;

    @Autowired
    private FormulaCompositionPort formulas;

    @Autowired
    private AllergenFactsPort allergenFacts;

    @Autowired
    private com.spectrace.validation.application.port.RuleSetVersionRepository ruleSets;

    @Autowired
    private com.spectrace.validation.application.port.ValidationRunRepository runs;

    @Autowired
    private com.spectrace.validation.application.port.ValidationResultRepository results;

    @Autowired
    private com.spectrace.audit.application.port.AuditEventPort auditEvents;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private AuthorizationService authorizationService;

    private String runId;

    @BeforeEach
    void restoreCurrentFixtureSelection() {
        jdbcTemplate.update(
                "UPDATE formula_version SET is_current_released = 'N' WHERE product_id = ?",
                "prod_usda_1106285");
        jdbcTemplate.update(
                "UPDATE formula_version SET lifecycle_status = 'RELEASED', is_current_released = 'Y' " +
                        "WHERE formula_version_id = ?", CURRENT_FORMULA);
        jdbcTemplate.update(
                "UPDATE product SET current_formula_version_id = ?, current_published_label_version_id = ? " +
                        "WHERE product_id = ?", CURRENT_FORMULA, CURRENT_LABEL, "prod_usda_1106285");
    }

    @AfterEach
    void cleanUp() {
        if (runId != null) {
            jdbcTemplate.update("DELETE FROM audit_event WHERE correlation_id = ?", runId);
            jdbcTemplate.update("DELETE FROM validation_result WHERE validation_run_id = ?", runId);
            jdbcTemplate.update("DELETE FROM validation_run WHERE validation_run_id = ?", runId);
        }
    }

    @Test
    void ownerAdaptersExposeCurrentLabelFormulaAndVersionScopedAllergenEvidence() {
        var label = labelSnapshots.findById(CURRENT_LABEL).orElseThrow();
        var formula = formulas.findById(CURRENT_FORMULA).orElseThrow();

        assertThat(label.isCurrent()).isTrue();
        assertThat(label.formulaVersionId()).isEqualTo(CURRENT_FORMULA);
        assertThat(label.declarations()).extracting(
                com.spectrace.label.application.port.LabelValidationSnapshot.AllergenDeclaration::allergenId)
                .containsExactly("all_soy", "all_wheat");
        assertThat(formula.isCurrentReleased()).isTrue();
        assertThat(formula.items()).hasSize(3);

        var derivation = allergenFacts.derive(formula, ACTIVE_RULE_SET, "US");
        assertThat(derivation.formulaVersionId()).isEqualTo(CURRENT_FORMULA);
        assertThat(derivation.facts()).extracting(
                com.spectrace.allergen.application.port.AllergenFact::allergenCode)
                .containsExactly("SOY", "WHEAT");
        assertThat(derivation.facts()).allSatisfy(fact ->
                assertThat(fact.derivationEvidence()).isNotEmpty());
        assertThat(derivation.unresolvedComponents()).isEmpty();
    }

    @Test
    void missingLabelIsAbsentAndUnknownJurisdictionDoesNotReuseCanonicalRows() {
        assertThat(labelSnapshots.findById("missing-label-version")).isEmpty();
        assertThat(allergenFacts.listAllergens("US")).isNotEmpty();
        assertThat(allergenFacts.listAllergens("UNKNOWN")).isEmpty();
    }

    @Test
    @Transactional
    void repositoriesAndAuditRoundTripThroughMySqlInOneTransaction() {
        runId = "scrum29-" + UUID.randomUUID();
        Instant ranAt = Instant.parse("2026-09-14T02:00:00Z");
        ValidationRun run = new ValidationRun(
                runId, CURRENT_LABEL, ACTIVE_RULE_SET, ValidationStatus.PASSED,
                "user_label_officer", ranAt, "integration evidence", "prov_project_seed");
        ValidationResult result = new ValidationResult(
                "result-" + runId, runId, "rule_v1_soy_ingredient", "SOY_DECLARATION_PRESENT",
                ValidationSeverity.INFO, true, false, "Soy declaration is present");

        runs.save(run);
        results.saveAll(runId, java.util.List.of(result));
        auditEvents.recordValidationEvent(
                run.ranByUserId(), run.labelVersionId(), run.validationRunId(),
                run.ruleSetVersionId(), run.dataProvenanceId());

        assertThat(runs.findById(runId)).contains(run);
        assertThat(results.findByRunId(runId)).containsExactly(result);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE correlation_id = ? AND actor_user_id = ? " +
                        "AND entity_id = ?", Integer.class,
                runId, run.ranByUserId(), run.labelVersionId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM audit_event WHERE correlation_id = ?", String.class, runId))
                .isEqualTo("LABEL_VALIDATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ran_at FROM validation_run WHERE validation_run_id = ?", java.sql.Timestamp.class, runId)
        ).isNotNull();
    }

    @Test
    void requestAuthorizationAdapterPreservesAuthenticationAndAuthorizationSemantics() {
        var audit = (com.spectrace.audit.application.port.AuditEventPort)
                (actor, label, validationRun, ruleSet, provenance) -> { };

        var authorized = adapter(AUTHORIZED_SUBJECT, audit);
        assertThat(authorized.requireActor(RequestAuthorizationAdapter.VALIDATE_LABEL_PERMISSION))
                .isEqualTo("user_label_officer");

        var unauthorized = adapter(UNAUTHORIZED_SUBJECT, audit);
        assertThatThrownBy(() -> unauthorized.requireActor(RequestAuthorizationAdapter.VALIDATE_LABEL_PERMISSION))
                .isInstanceOf(AuthorizationDeniedException.class);

        var unknown = adapter("unknown-subject", audit);
        assertThatThrownBy(() -> unknown.requireActor(RequestAuthorizationAdapter.VALIDATE_LABEL_PERMISSION))
                .isInstanceOf(com.spectrace.identity.application.UnknownIdentityException.class);
    }

    @Test
    void ruleSetLifecycleAndFlywayBaselineRemainAvailableToM5() {
        assertThat(ruleSets.findActiveById(ACTIVE_RULE_SET)).isPresent();
        assertThat(ruleSets.findActiveById("ruleset_us_falcpa_demo_v2")).isEmpty();
        assertThat(ruleSets.findById("ruleset_us_falcpa_demo_v2")).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class))
                .isEqualTo(5);
    }

    private RequestAuthorizationAdapter adapter(
            String externalSubject,
            com.spectrace.audit.application.port.AuditEventPort audit
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestAuthorizationAdapter.AUTH_PROVIDER_HEADER, "DEV_EXTERNAL");
        request.addHeader(RequestAuthorizationAdapter.AUTH_SUBJECT_HEADER, externalSubject);
        return new RequestAuthorizationAdapter(request, identityService, authorizationService, audit);
    }
}
