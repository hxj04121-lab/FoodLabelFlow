package com.spectrace.validation.interfaces.web;

import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationRunRequest;
import com.spectrace.validation.application.ValidationRunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ValidationController {

    private final ValidationApplicationService service;

    public ValidationController(ValidationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/label-versions/{labelVersionId}/validation-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ValidationRunResponse validate(
            @PathVariable String labelVersionId,
            @RequestBody ValidationRunRequest request
    ) {
        return service.validate(labelVersionId, request);
    }

    @GetMapping("/validation-runs/{validationRunId}")
    public ValidationRunResponse get(@PathVariable String validationRunId) {
        return service.get(validationRunId);
    }
}
