package com.spectrace.validation.application;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Immutable application input for a validation run. */
public record ValidationRunRequest(String ruleSetVersionId) {
    public ValidationRunRequest {
        ruleSetVersionId = requiredText(ruleSetVersionId, "ruleSetVersionId");
    }
}
