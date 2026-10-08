package com.spectrace.impact.interfaces.web;

import com.spectrace.impact.application.ChangeImpactAnalysisService;
import com.spectrace.impact.application.ImpactFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

/** HTTP adapter for the S3 impact-analysis trigger and query; policy stays in ChangeImpactAnalysisService. */
@RestController
public class ImpactAnalysisController {
    private static final JsonMapper REQUEST_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> TRIGGER_FIELDS = Set.of("ruleSetVersionId");

    private final ChangeImpactAnalysisService analyses;

    public ImpactAnalysisController(ChangeImpactAnalysisService analyses) {
        this.analyses = analyses;
    }

    /** 201 for a new analysis, 200 for an idempotent replay with the same rule set. */
    @PostMapping(value = "/api/v1/change-requests/{changeRequestId}/impact-analyses",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImpactAnalysisResponse> run(
            @PathVariable String changeRequestId, @RequestBody String body) {
        var view = analyses.run(changeRequestId, parseRuleSetVersionId(body));
        var response = ImpactAnalysisResponse.from(view);
        if (!view.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/impact-analyses/" + response.impactAnalysisId()))
                .body(response);
    }

    @GetMapping(value = "/api/v1/impact-analyses/{impactAnalysisId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ImpactAnalysisResponse get(@PathVariable String impactAnalysisId) {
        return ImpactAnalysisResponse.from(analyses.get(impactAnalysisId));
    }

    private static String parseRuleSetVersionId(String body) {
        JsonNode input;
        try {
            input = REQUEST_JSON.readTree(body);
        } catch (JacksonException error) {
            throw ImpactFailure.invalid("The request must be a JSON impact analysis trigger object");
        }
        if (input == null || !input.isObject() || !input.properties().stream()
                .map(Map.Entry::getKey).collect(Collectors.toSet()).equals(TRIGGER_FIELDS)) {
            throw ImpactFailure.invalid("The request must contain exactly ruleSetVersionId");
        }
        JsonNode value = input.get("ruleSetVersionId");
        if (!value.isString() || value.stringValue().isBlank()) {
            throw ImpactFailure.invalid("ruleSetVersionId must be a non-blank string");
        }
        return value.stringValue();
    }
}
