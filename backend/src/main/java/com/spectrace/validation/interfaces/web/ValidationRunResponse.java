package com.spectrace.validation.interfaces.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrace.validation.application.ValidationApplicationService.RunDetails;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationStatus;

import java.util.List;

/** Deliberately excludes M5 persistence IDs, actor and provenance from the frozen HTTP resource. */
public record ValidationRunResponse(
        String validationRunId,
        String labelVersionId,
        String ruleSetVersionId,
        ValidationStatus status,
        String ranAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String summary,
        List<ValidationFinding> results
) {
    public static ValidationRunResponse from(RunDetails details) {
        var run = details.run();
        return new ValidationRunResponse(
                run.validationRunId(), run.labelVersionId(), run.ruleSetVersionId(), run.status(),
                run.ranAt().toString(), run.summary(),
                details.results().stream().map(result -> new ValidationFinding(
                        result.ruleDefinitionId(), result.resultCode(), result.severity(),
                        result.passed(), result.blocking(), result.message())).toList());
    }
}
