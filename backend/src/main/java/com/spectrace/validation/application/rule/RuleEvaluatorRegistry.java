package com.spectrace.validation.application.rule;

import com.spectrace.validation.domain.RuleType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable SAD-003 dispatch table. Configuration mistakes must never yield a pass. */
public final class RuleEvaluatorRegistry {
    private final Map<RuleType, RuleEvaluator> evaluators;

    public RuleEvaluatorRegistry(Collection<? extends RuleEvaluator> evaluators) {
        var byType = new EnumMap<RuleType, RuleEvaluator>(RuleType.class);
        for (RuleEvaluator evaluator : evaluators) {
            Objects.requireNonNull(evaluator, "evaluator");
            RuleType type = Objects.requireNonNull(evaluator.ruleType(), "ruleType");
            if (byType.putIfAbsent(type, evaluator) != null) {
                throw new IllegalArgumentException("Duplicate evaluator for " + type);
            }
        }
        this.evaluators = Map.copyOf(byType);
    }

    /** Preflight every active rule type before evaluation or any persistence write. */
    public RuleEvaluator require(RuleType type) {
        Objects.requireNonNull(type, "ruleType");
        RuleEvaluator evaluator = evaluators.get(type);
        if (evaluator == null) {
            throw new IllegalStateException("No evaluator registered for " + type);
        }
        return evaluator;
    }
}
