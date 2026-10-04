package com.spectrace.impact.application.strategy;

import com.spectrace.impact.domain.ChangeType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable change-type dispatch table, built like the Sprint 2 RuleEvaluatorRegistry.
 * A duplicate strategy, or a missing one for a required type, fails construction and so
 * fails application startup; a configuration mistake never yields a classification.
 */
public final class ImpactStrategyRegistry {
    private final Map<ChangeType, ImpactStrategy> strategies;

    public ImpactStrategyRegistry(Collection<? extends ImpactStrategy> strategies, Set<ChangeType> requiredTypes) {
        var byType = new EnumMap<ChangeType, ImpactStrategy>(ChangeType.class);
        for (ImpactStrategy strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            ChangeType type = Objects.requireNonNull(strategy.changeType(), "changeType");
            if (byType.putIfAbsent(type, strategy) != null) {
                throw new IllegalArgumentException("Duplicate impact strategy for " + type);
            }
        }
        for (ChangeType required : requiredTypes) {
            if (!byType.containsKey(Objects.requireNonNull(required, "requiredType"))) {
                throw new IllegalStateException("No impact strategy registered for " + required);
            }
        }
        this.strategies = Map.copyOf(byType);
    }

    public ImpactStrategy require(ChangeType type) {
        Objects.requireNonNull(type, "changeType");
        ImpactStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No impact strategy registered for " + type);
        }
        return strategy;
    }
}
