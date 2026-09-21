package com.spectrace.validation.interfaces.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Explicit HTTP shapes for the frozen validation contract. */
public final class ValidationApiDtos {
    private ValidationApiDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ValidationRunRequest(String ruleSetVersionId) {
    }

    public record AllergenResponse(
            String allergenId,
            String allergenCode,
            String displayName,
            String jurisdictionCode
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidationRunResponse(
            String validationRunId,
            String labelVersionId,
            String ruleSetVersionId,
            String status,
            Instant ranAt,
            String summary,
            java.util.List<ValidationResultResponse> results
    ) {
    }

    public record ValidationResultResponse(
            String ruleDefinitionId,
            String resultCode,
            String severity,
            boolean passed,
            boolean blocking,
            String message
    ) {
    }
}
