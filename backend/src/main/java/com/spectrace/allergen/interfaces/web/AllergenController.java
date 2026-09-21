package com.spectrace.allergen.interfaces.web;

import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.interfaces.web.ValidationApiDtos.AllergenResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/allergens")
public class AllergenController {
    private final AllergenFactsPort allergens;
    private final ValidationIntegration integration;

    public AllergenController(AllergenFactsPort allergens, ValidationIntegration integration) {
        this.allergens = allergens;
        this.integration = integration;
    }

    @GetMapping
    public List<AllergenResponse> list(@RequestParam String jurisdictionCode) {
        if (jurisdictionCode == null || jurisdictionCode.isBlank()) {
            throw ValidationFailure.invalid("jurisdictionCode must be supplied");
        }
        integration.requireActor(ValidationOrchestrator.VALIDATE_PERMISSION);
        return allergens.listAllergens(jurisdictionCode).stream().map(this::toResponse).toList();
    }

    private AllergenResponse toResponse(AllergenEntry value) {
        return new AllergenResponse(
                value.allergenId(),
                value.allergenCode(),
                value.displayName(),
                value.jurisdictionCode());
    }
}
