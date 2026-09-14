package com.spectrace.validation.application;

import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;

import java.time.Instant;
import java.util.List;

public record ValidationRunResponse(
        String validationRunId,
        String labelVersionId,
        String ruleSetVersionId,
        String status,
        Instant ranAt,
        String summary,
        List<ValidationResultResponse> results
) {
    public static ValidationRunResponse from(ValidationRun run, List<ValidationResult> results) {
        return new ValidationRunResponse(
                run.validationRunId(),
                run.labelVersionId(),
                run.ruleSetVersionId(),
                run.status().name(),
                run.ranAt(),
                run.summary(),
                results.stream().map(ValidationResultResponse::from).toList()
        );
    }

    public record ValidationResultResponse(
            String ruleDefinitionId,
            String resultCode,
            String severity,
            boolean passed,
            boolean blocking,
            String message
    ) {
        private static ValidationResultResponse from(ValidationResult result) {
            return new ValidationResultResponse(
                    result.ruleDefinitionId(),
                    result.resultCode(),
                    result.severity().name(),
                    result.passed(),
                    result.blocking(),
                    result.message()
            );
        }
    }
}
