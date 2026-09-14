package com.spectrace.validation;

import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ValidationFindingTest {
    @ParameterizedTest
    @EnumSource(ValidationSeverity.class)
    void preservesRuleAttributionAndOnlyFailedErrorsBlock(ValidationSeverity severity) {
        var rule = new RuleDefinition("rule-1", "rules-1", "DECLARATION", RuleType.LABEL_DECLARATION_VALIDATION,
                null, "CONTAINS", severity, true, "Test declaration rule");
        var failed = ValidationFinding.forRule(rule, "DECLARATION_CHECK", false, "A declaration is missing");
        var passed = ValidationFinding.forRule(rule, "DECLARATION_CHECK", true, "Declarations agree");

        assertThat(failed.ruleDefinitionId()).isEqualTo("rule-1");
        assertThat(failed.severity()).isEqualTo(severity);
        assertThat(failed.blocking()).isEqualTo(severity == ValidationSeverity.ERROR);
        assertThat(passed.passed()).isTrue();
        assertThat(passed.blocking()).isFalse();
    }

    @Test
    void contradictoryBlockingFlagsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ValidationFinding(null, "CHECK", ValidationSeverity.ERROR, true, true, "Contradiction"));
        for (var severity : new ValidationSeverity[]{ValidationSeverity.INFO, ValidationSeverity.WARNING}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ValidationFinding(null, "CHECK", severity, false, true, "Contradiction"));
        }
    }

    @Test
    void inputFindingsMayHaveNoRuleButCannotInventBlankIdentifiersOrMessages() {
        assertThat(new ValidationFinding(null, "INPUT_CHECK", ValidationSeverity.ERROR, false, true,
                "An ingredient is unresolved").ruleDefinitionId()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ValidationFinding(" ", "CHECK", ValidationSeverity.ERROR, false, true, "Failure"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ValidationFinding(null, "CHECK", ValidationSeverity.ERROR, false, true, " "));
    }
}
