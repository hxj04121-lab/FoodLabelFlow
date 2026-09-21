package com.spectrace.validation.application;

import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Reads a persisted run through validation-owned ports after trusted auth. */
@Service
@Transactional(readOnly = true)
public class ValidationRunQueryService {
    private final ValidationRunRepository runs;
    private final ValidationResultRepository results;
    private final ValidationIntegration integration;

    public ValidationRunQueryService(
            ValidationRunRepository runs,
            ValidationResultRepository results,
            ValidationIntegration integration
    ) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.results = Objects.requireNonNull(results, "results");
        this.integration = Objects.requireNonNull(integration, "integration");
    }

    public ValidationRunDetails find(String validationRunId) {
        if (validationRunId == null || validationRunId.isBlank()) {
            throw ValidationFailure.invalid("validationRunId must be supplied");
        }
        integration.requireActor(ValidationOrchestrator.VALIDATE_PERMISSION);
        ValidationRun run = runs.findById(validationRunId)
                .orElseThrow(() -> ValidationFailure.notFound(
                        "The requested validation run was not found"));
        return new ValidationRunDetails(run, results.findByRunId(run.validationRunId()));
    }

    public record ValidationRunDetails(ValidationRun run, List<ValidationResult> results) {
        public ValidationRunDetails {
            run = Objects.requireNonNull(run, "run");
            results = List.copyOf(Objects.requireNonNull(results, "results"));
        }
    }
}
