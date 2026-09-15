package com.spectrace.validation.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record RuleSetVersion(
        String ruleSetVersionId,
        String ruleSetCode,
        String versionNumber,
        String jurisdictionCode,
        RuleSetLifecycleStatus lifecycleStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean demoOnly,
        String description,
        String dataProvenanceId,
        List<RuleDefinition> ruleDefinitions
) {
    public RuleSetVersion {
        ruleSetVersionId = required(ruleSetVersionId, "ruleSetVersionId");
        ruleSetCode = required(ruleSetCode, "ruleSetCode");
        versionNumber = required(versionNumber, "versionNumber");
        jurisdictionCode = required(jurisdictionCode, "jurisdictionCode");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        description = required(description, "description");
        dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must not precede effectiveFrom");
        }
        ruleDefinitions = List.copyOf(Objects.requireNonNull(ruleDefinitions, "ruleDefinitions"));
        for (RuleDefinition rule : ruleDefinitions) {
            if (!ruleSetVersionId.equals(rule.ruleSetVersionId())) {
                throw new IllegalArgumentException("rule definitions must belong to this rule set version");
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
