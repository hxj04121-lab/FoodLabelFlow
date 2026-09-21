package com.spectrace.validation.interfaces.web;

import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.ValidationRunRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;

/** HTTP adapter for the frozen SCRUM-41 contract; business policy remains in the application. */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ValidationController {
    // Local strict parsing does not change the JSON behaviour of other modules.
    private static final JsonMapper REQUEST_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final ValidationApplicationService validation;
    private final AllergenFactsPort allergens;
    private final IdentityService identities;

    public ValidationController(
            ValidationApplicationService validation, AllergenFactsPort allergens, IdentityService identities
    ) {
        this.validation = validation;
        this.allergens = allergens;
        this.identities = identities;
    }

    @GetMapping("/allergens")
    public List<AllergenEntry> listAllergens(
            @RequestParam String jurisdictionCode, HttpServletRequest request
    ) {
        authenticateRead(request);
        if (jurisdictionCode.isBlank()) {
            throw ValidationFailure.invalid("jurisdictionCode is required");
        }
        return allergens.listAllergens(jurisdictionCode);
    }

    @PostMapping(value = "/label-versions/{labelVersionId}/validation-runs",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationRunResponse> validate(
            @PathVariable String labelVersionId, @RequestBody String body
    ) {
        ValidationRunRequest input = parseRequest(body);
        // This proxied call returns only after the outer run/results/audit transaction commits.
        var details = validation.validateWithResults(labelVersionId, input.ruleSetVersionId());
        return ResponseEntity.created(URI.create("/api/v1/validation-runs/" + details.run().validationRunId()))
                .body(ValidationRunResponse.from(details));
    }

    @GetMapping("/validation-runs/{validationRunId}")
    public ValidationRunResponse getRun(@PathVariable String validationRunId, HttpServletRequest request) {
        authenticateRead(request);
        return ValidationRunResponse.from(validation.getRun(validationRunId));
    }

    private static ValidationRunRequest parseRequest(String body) {
        JsonNode input;
        try {
            input = REQUEST_JSON.readTree(body);
        } catch (JacksonException error) {
            throw ValidationFailure.invalid("The request must be a JSON object containing only ruleSetVersionId");
        }
        if (input == null || !input.isObject() || input.size() != 1
                || !input.has("ruleSetVersionId") || !input.get("ruleSetVersionId").isString()
                || input.get("ruleSetVersionId").stringValue().isBlank()) {
            throw ValidationFailure.invalid("ruleSetVersionId must be the only field and a non-blank string");
        }
        return new ValidationRunRequest(input.get("ruleSetVersionId").stringValue());
    }

    /** Same active-identity read policy as M4's label GET; writes separately require LABEL.VALIDATE. */
    private void authenticateRead(HttpServletRequest request) {
        String provider = request.getHeader("X-Auth-Provider");
        String subject = request.getHeader("X-External-Subject");
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new UnknownIdentityException("Authenticated identity headers are required");
        }
        identities.authenticate(provider, subject);
    }
}
