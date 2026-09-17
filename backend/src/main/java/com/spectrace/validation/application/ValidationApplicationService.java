package com.spectrace.validation.application;

import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns the transaction from trusted input capture through synchronous audit. */
public class ValidationApplicationService {
    private final ValidationOrchestrator orchestrator;
    private final ValidationRunRepository runs;
    private final ValidationResultRepository results;
    private final ValidationIntegration integration;
    private final Clock clock;

    public ValidationApplicationService(
            ValidationOrchestrator orchestrator,
            ValidationRunRepository runs,
            ValidationResultRepository results,
            ValidationIntegration integration,
            Clock clock
    ) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.results = Objects.requireNonNull(results, "results");
        this.integration = Objects.requireNonNull(integration, "integration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A completed FAILED evaluation is persisted just like a PASSED evaluation. */
    @Transactional
    public ValidationRun validate(String labelVersionId, String ruleSetVersionId) {
        ValidationEvaluation evaluation = orchestrator.orchestrate(labelVersionId, ruleSetVersionId);
        ValidationRun run = new ValidationRun(
                UUID.randomUUID().toString(),
                evaluation.label().labelVersionId(),
                evaluation.ruleSet().ruleSetVersionId(),
                evaluation.status(),
                evaluation.actorId(),
                // The canonical MySQL DATETIME column stores whole UTC seconds.
                clock.instant().truncatedTo(ChronoUnit.SECONDS),
                null,
                evaluation.label().dataProvenanceId());
        List<ValidationResult> persistedResults = evaluation.findings().stream()
                .map(finding -> new ValidationResult(
                        UUID.randomUUID().toString(), run.validationRunId(),
                        finding.ruleDefinitionId(), finding.resultCode(), finding.severity(),
                        finding.passed(), finding.blocking(), finding.message()))
                .toList();

        runs.save(run);
        results.saveAll(run.validationRunId(), persistedResults);
        integration.auditValidation(
                run.ranByUserId(), run.labelVersionId(), run.ruleSetVersionId(),
                run.validationRunId(), run.dataProvenanceId());
        return run;
    }
}
