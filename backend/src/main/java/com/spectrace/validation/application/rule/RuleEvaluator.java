package com.spectrace.validation.application.rule;

import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;

import java.util.List;

/** SAD-003 Strategy: one type per evaluator; no persistence, identity or audit effects. */
public interface RuleEvaluator {
    RuleType ruleType();

    /**
     * Evaluate one active definition from context.ruleSet. Return all findings in
     * stable order, attributed to that definition; do not stop after the first failure.
     */
    List<ValidationFinding> evaluate(RuleDefinition rule, RuleEvaluationContext context);
}
