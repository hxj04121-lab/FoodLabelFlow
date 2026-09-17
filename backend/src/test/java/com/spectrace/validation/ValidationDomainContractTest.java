package com.spectrace.validation;

import com.spectrace.validation.application.ValidationRunRequest;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ValidationDomainContractTest {

    @Test
    void requestsRunsAndResultsRequireExplicitIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ValidationRunRequest(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ValidationRunRequest(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> run(null));
        assertThatIllegalArgumentException().isThrownBy(() -> result(" ", false, true, ValidationSeverity.ERROR));
        assertThatIllegalArgumentException().isThrownBy(() -> new ValidationResult(
                null, "run-1", null, "CHECK", ValidationSeverity.ERROR, false, true, "message"));
    }

    @Test
    void ruleSetVersionsValidateLifecycleDatesOwnershipAndImmutableDefinitions() {
        RuleDefinition definition = definition("rules-1");
        var definitions = new ArrayList<>(List.of(definition));
        RuleSetVersion version = new RuleSetVersion(
                "rules-1", "TEST", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 9, 1), null, true, "Test rules", "prov-test", definitions);
        definitions.clear();

        assertThat(version.ruleDefinitions()).containsExactly(definition);
        assertThatIllegalArgumentException().isThrownBy(() -> new RuleSetVersion(
                "rules-1", "TEST", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), true,
                "Test rules", "prov-test", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RuleSetVersion(
                "rules-1", "TEST", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 9, 1), null, true, "Test rules", "prov-test",
                List.of(definition("other-rules"))));
        assertThatNullPointerException().isThrownBy(() -> new RuleSetVersion(
                "rules-1", "TEST", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 9, 1), null, true, "Test rules", "prov-test", null));
    }

    @Test
    void onlyActiveRuleSetLifecycleIsExecutable() {
        assertThat(RuleSetLifecycleStatus.ACTIVE.isExecutable()).isTrue();
        assertThat(RuleSetLifecycleStatus.DRAFT.isExecutable()).isFalse();
        assertThat(RuleSetLifecycleStatus.RETIRED.isExecutable()).isFalse();
    }

    @Test
    void resultRejectsContradictoryBlockingFlags() {
        assertThatIllegalArgumentException().isThrownBy(() -> result(null, true, true, ValidationSeverity.ERROR));
        assertThatIllegalArgumentException().isThrownBy(() -> result(null, false, true, ValidationSeverity.WARNING));
        assertThat(result(null, false, false, ValidationSeverity.ERROR).blocking()).isFalse();
    }

    @Test
    void aggregateStatusUsesOnlyFailedBlockingResults() {
        assertThat(ValidationStatus.fromResults(List.of(
                result(null, true, false, ValidationSeverity.INFO),
                result(null, false, false, ValidationSeverity.WARNING))))
                .isEqualTo(ValidationStatus.PASSED);
        assertThat(ValidationStatus.fromResults(List.of(
                result("rule-1", false, true, ValidationSeverity.ERROR))))
                .isEqualTo(ValidationStatus.FAILED);
        assertThat(ValidationStatus.fromResults(List.of())).isEqualTo(ValidationStatus.PASSED);
        var nullResult = new ArrayList<ValidationResult>();
        nullResult.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> ValidationStatus.fromResults(nullResult));
    }

    private ValidationResult result(String ruleId, boolean passed, boolean blocking, ValidationSeverity severity) {
        return new ValidationResult("result-1", "run-1", ruleId, "CHECK", severity,
                passed, blocking, "message");
    }

    private RuleDefinition definition(String ruleSetId) {
        return new RuleDefinition("rule-1", ruleSetId, "CHECK", RuleType.LABEL_DECLARATION_VALIDATION,
                null, "CONTAINS", ValidationSeverity.ERROR, true, "Test rule");
    }

    private com.spectrace.validation.domain.ValidationRun run(String ruleSetId) {
        return new com.spectrace.validation.domain.ValidationRun(
                "run-1", "label-1", ruleSetId, ValidationStatus.PASSED,
                "user-1", Instant.parse("2026-09-15T00:00:00Z"), null, "prov-test");
    }
}
