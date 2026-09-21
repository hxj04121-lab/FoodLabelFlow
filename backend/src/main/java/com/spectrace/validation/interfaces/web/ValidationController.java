package com.spectrace.validation.interfaces.web;

import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.ValidationRunQueryService;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.spectrace.validation.interfaces.web.ValidationApiDtos.ValidationResultResponse;
import static com.spectrace.validation.interfaces.web.ValidationApiDtos.ValidationRunRequest;
import static com.spectrace.validation.interfaces.web.ValidationApiDtos.ValidationRunResponse;

@RestController
@RequestMapping("/api/v1")
public class ValidationController {
    private final ValidationApplicationService validation;
    private final ValidationRunQueryService queries;

    public ValidationController(
            ValidationApplicationService validation,
            ValidationRunQueryService queries
    ) {
        this.validation = validation;
        this.queries = queries;
    }

    @PostMapping("/label-versions/{labelVersionId}/validation-runs")
    public ResponseEntity<ValidationRunResponse> validate(
            @PathVariable String labelVersionId,
            @RequestBody ValidationRunRequest request
    ) {
        if (request == null || request.ruleSetVersionId() == null
                || request.ruleSetVersionId().isBlank()) {
            throw ValidationFailure.invalid("ruleSetVersionId must be supplied");
        }
        ValidationRun run = validation.validate(labelVersionId, request.ruleSetVersionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(queries.find(run.validationRunId())));
    }

    @GetMapping("/validation-runs/{validationRunId}")
    public ValidationRunResponse get(@PathVariable String validationRunId) {
        return toResponse(queries.find(validationRunId));
    }

    private ValidationRunResponse toResponse(ValidationRunQueryService.ValidationRunDetails details) {
        ValidationRun run = details.run();
        return new ValidationRunResponse(
                run.validationRunId(),
                run.labelVersionId(),
                run.ruleSetVersionId(),
                run.status().name(),
                run.ranAt(),
                run.summary(),
                details.results().stream().map(this::toResponse).toList());
    }

    private ValidationResultResponse toResponse(ValidationResult result) {
        return new ValidationResultResponse(
                result.ruleDefinitionId(),
                result.resultCode(),
                result.severity().name(),
                result.passed(),
                result.blocking(),
                result.message());
    }
}
