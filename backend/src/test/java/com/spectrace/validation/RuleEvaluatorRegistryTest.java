package com.spectrace.validation;

import com.spectrace.validation.application.rule.RuleEvaluationContext;
import com.spectrace.validation.application.rule.RuleEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluatorRegistry;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RuleEvaluatorRegistryTest {
    @Test
    void resolvesEveryCanonicalTypeRegardlessOfRegistrationOrder() {
        RuleEvaluator declaration = new StubEvaluator(RuleType.LABEL_DECLARATION_VALIDATION);
        RuleEvaluator ingredient = new StubEvaluator(RuleType.INGREDIENT_TO_ALLERGEN);
        var registry = new RuleEvaluatorRegistry(List.of(declaration, ingredient));

        assertThat(registry.require(RuleType.INGREDIENT_TO_ALLERGEN)).isSameAs(ingredient);
        assertThat(registry.require(RuleType.LABEL_DECLARATION_VALIDATION)).isSameAs(declaration);
    }

    @Test
    void refusesDuplicateTypeEvenWhenTheSameInstanceIsRegisteredTwice() {
        var evaluator = new StubEvaluator(RuleType.INGREDIENT_TO_ALLERGEN);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleEvaluatorRegistry(List.of(evaluator, evaluator)))
                .withMessageContaining("Duplicate evaluator for INGREDIENT_TO_ALLERGEN");
    }

    @Test
    void missingTypeIsAnExplicitConfigurationFailure() {
        var registry = new RuleEvaluatorRegistry(List.of(
                new StubEvaluator(RuleType.INGREDIENT_TO_ALLERGEN)));
        assertThatIllegalStateException()
                .isThrownBy(() -> registry.require(RuleType.LABEL_DECLARATION_VALIDATION))
                .withMessageContaining("No evaluator registered for LABEL_DECLARATION_VALIDATION");
        assertThatIllegalStateException()
                .isThrownBy(() -> new RuleEvaluatorRegistry(List.of())
                        .require(RuleType.INGREDIENT_TO_ALLERGEN));
    }

    @Test
    void callerCannotChangeRegistryByMutatingRegistrationList() {
        var evaluator = new StubEvaluator(RuleType.INGREDIENT_TO_ALLERGEN);
        var registrations = new ArrayList<RuleEvaluator>(List.of(evaluator));
        var registry = new RuleEvaluatorRegistry(registrations);
        registrations.clear();
        registrations.add(new StubEvaluator(RuleType.LABEL_DECLARATION_VALIDATION));

        assertThat(registry.require(RuleType.INGREDIENT_TO_ALLERGEN)).isSameAs(evaluator);
        assertThatIllegalStateException()
                .isThrownBy(() -> registry.require(RuleType.LABEL_DECLARATION_VALIDATION));
    }

    @Test
    void nullAndUnknownTypesCannotBecomeFallbackRules() {
        assertThatNullPointerException().isThrownBy(() -> new RuleEvaluatorRegistry(
                Arrays.asList((RuleEvaluator) null)));
        assertThatNullPointerException().isThrownBy(() -> new RuleEvaluatorRegistry(
                List.of(new StubEvaluator(null))));
        assertThatNullPointerException().isThrownBy(() ->
                new RuleEvaluatorRegistry(List.of()).require(null));
        assertThatIllegalStateException().isThrownBy(() -> RuleType.fromDatabase("UNKNOWN_RULE"));
    }

    private record StubEvaluator(RuleType ruleType) implements RuleEvaluator {
        @Override
        public List<ValidationFinding> evaluate(RuleDefinition rule, RuleEvaluationContext context) {
            throw new UnsupportedOperationException("Registry tests do not evaluate business rules");
        }
    }
}
