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

/** M5 transaction seam for the official M1 validation orchestration. */
@Service
public class ValidationPersistenceService {

    private final ValidationRunRepository runs;
    private final ValidationResultRepository results;
    private final ValidationIntegration integration;

    public ValidationPersistenceService(
            ValidationRunRepository runs,
            ValidationResultRepository results,
            ValidationIntegration integration
    ) {
        this.runs = runs;
        this.results = results;
        this.integration = integration;
    }

    @Transactional
    public void persist(ValidationRun run, List<ValidationResult> findings) {
        Objects.requireNonNull(run, "run");
        List<ValidationResult> values = List.copyOf(Objects.requireNonNull(findings, "findings"));

        runs.save(run);
        results.saveAll(run.validationRunId(), values);
        integration.auditValidation(
                run.ranByUserId(), run.labelVersionId(), run.ruleSetVersionId(),
                run.validationRunId(), run.dataProvenanceId(), run.status().name(), run.summary());
    }
}
