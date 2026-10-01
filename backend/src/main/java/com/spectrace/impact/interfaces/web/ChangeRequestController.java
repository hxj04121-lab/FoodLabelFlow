package com.spectrace.impact.interfaces.web;

import com.spectrace.impact.application.ChangeRequestService;
import com.spectrace.impact.application.CreateIngredientSpecChange;
import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.domain.ChangeType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** HTTP adapter for INGREDIENT_SPEC change requests; business policy stays in ChangeRequestService. */
@RestController
@RequestMapping(value = "/api/v1/change-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChangeRequestController {
    // Local strict parsing does not change the JSON behaviour of other modules.
    private static final JsonMapper REQUEST_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> CREATE_FIELDS = Set.of(
            "changeType", "supplierMaterialId", "previousSpecificationVersionId",
            "targetSpecificationVersionId", "description");

    private final ChangeRequestService changeRequests;

    public ChangeRequestController(ChangeRequestService changeRequests) {
        this.changeRequests = changeRequests;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChangeRequestResponse> create(@RequestBody String body) {
        var created = ChangeRequestResponse.from(changeRequests.create(parseCreate(body)));
        return ResponseEntity.created(URI.create("/api/v1/change-requests/" + created.changeRequestId()))
                .body(created);
    }

    @GetMapping("/{changeRequestId}")
    public ChangeRequestResponse get(@PathVariable String changeRequestId) {
        return ChangeRequestResponse.from(changeRequests.get(changeRequestId));
    }

    private static CreateIngredientSpecChange parseCreate(String body) {
        JsonNode input;
        try {
            input = REQUEST_JSON.readTree(body);
        } catch (JacksonException error) {
            throw ImpactFailure.invalid("The request must be a JSON change request object");
        }
        if (input == null || !input.isObject() || !fieldNames(input).equals(CREATE_FIELDS)) {
            throw ImpactFailure.invalid("The request must contain exactly changeType, supplierMaterialId, "
                    + "previousSpecificationVersionId, targetSpecificationVersionId and description");
        }
        if (!ChangeType.INGREDIENT_SPEC.name().equals(text(input, "changeType"))) {
            throw ImpactFailure.invalid("changeType must be INGREDIENT_SPEC; other change types are not supported");
        }
        String description = text(input, "description");
        if (description.codePointCount(0, description.length()) > CreateIngredientSpecChange.MAX_DESCRIPTION_LENGTH) {
            throw ImpactFailure.invalid("description must be at most "
                    + CreateIngredientSpecChange.MAX_DESCRIPTION_LENGTH + " characters");
        }
        return new CreateIngredientSpecChange(
                text(input, "supplierMaterialId"),
                text(input, "previousSpecificationVersionId"),
                text(input, "targetSpecificationVersionId"),
                description);
    }

    private static Set<String> fieldNames(JsonNode input) {
        return input.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static String text(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw ImpactFailure.invalid(field + " must be a non-blank string");
        }
        return value.stringValue();
    }
}
