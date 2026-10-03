package com.spectrace.impact.application.strategy;

import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ImpactStrategyRegistryTest {
    private static final Set<ChangeType> REQUIRED = EnumSet.of(ChangeType.INGREDIENT_SPEC);

    @Test
    void resolvesEachRegisteredTypeRegardlessOfRegistrationOrder() {
        var formula = new StubStrategy(ChangeType.FORMULA);
        var spec = new StubStrategy(ChangeType.INGREDIENT_SPEC);
        var registry = new ImpactStrategyRegistry(List.of(formula, spec), REQUIRED);

        assertThat(registry.require(ChangeType.INGREDIENT_SPEC)).isSameAs(spec);
        assertThat(registry.require(ChangeType.FORMULA)).isSameAs(formula);
    }

    @Test
    void aDuplicateTypeFailsConstructionEvenForTheSameInstance() {
        var spec = new StubStrategy(ChangeType.INGREDIENT_SPEC);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ImpactStrategyRegistry(List.of(spec, spec), REQUIRED))
                .withMessageContaining("Duplicate impact strategy for INGREDIENT_SPEC");
    }

    @Test
    void aMissingRequiredTypeFailsConstructionSoTheApplicationCannotStart() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new ImpactStrategyRegistry(List.of(), REQUIRED))
                .withMessageContaining("No impact strategy registered for INGREDIENT_SPEC");
        assertThatIllegalStateException()
                .isThrownBy(() -> new ImpactStrategyRegistry(List.of(new StubStrategy(ChangeType.FORMULA)), REQUIRED));
    }

    @Test
    void anExtensionPointWithoutAStrategyIsAnExplicitFailureNotAFallback() {
        var registry = new ImpactStrategyRegistry(List.of(new StubStrategy(ChangeType.INGREDIENT_SPEC)), REQUIRED);

        assertThatIllegalStateException().isThrownBy(() -> registry.require(ChangeType.FORMULA))
                .withMessageContaining("No impact strategy registered for FORMULA");
        assertThatIllegalStateException().isThrownBy(() -> registry.require(ChangeType.RULE_SET));
    }

    @Test
    void callerCannotChangeTheRegistryByMutatingTheRegistrationList() {
        var spec = new StubStrategy(ChangeType.INGREDIENT_SPEC);
        var registrations = new ArrayList<ImpactStrategy>(List.of(spec));
        var registry = new ImpactStrategyRegistry(registrations, REQUIRED);
        registrations.clear();
        registrations.add(new StubStrategy(ChangeType.RULE_SET));

        assertThat(registry.require(ChangeType.INGREDIENT_SPEC)).isSameAs(spec);
        assertThatIllegalStateException().isThrownBy(() -> registry.require(ChangeType.RULE_SET));
    }

    @Test
    void nullsAreRejectedRatherThanTreatedAsAType() {
        assertThatNullPointerException().isThrownBy(() -> new ImpactStrategyRegistry(
                Arrays.asList((ImpactStrategy) null), Set.of()));
        assertThatNullPointerException().isThrownBy(() -> new ImpactStrategyRegistry(
                List.of(new StubStrategy(null)), Set.of()));
        assertThatNullPointerException().isThrownBy(() ->
                new ImpactStrategyRegistry(List.of(), Set.of()).require(null));
    }

    private record StubStrategy(ChangeType changeType) implements ImpactStrategy {
        @Override
        public ProductImpactAssessment assess(ChangeRequest change, RelevantProductTarget product, String ruleSetVersionId) {
            throw new UnsupportedOperationException("Registry tests do not classify products");
        }
    }
}
