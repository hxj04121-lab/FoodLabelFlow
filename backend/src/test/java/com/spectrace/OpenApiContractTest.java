package com.spectrace;

import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    private static final Pattern LOCAL_REFERENCE = Pattern.compile(
            "\\$ref: '#/components/(?:schemas|responses|parameters)/([A-Za-z0-9]+)'");

    @Test
    void localReferencesAndCanonicalErrorSemanticsAreComplete() throws IOException {
        String contract = Files.readString(findContract());

        assertThat(contract).startsWith("openapi: 3.1.0");
        assertThat(contract).contains("required: [code, message, traceId, evidenceId]");
        assertThat(contract).contains("'400':", "'401':", "'403':", "'404':", "'409':", "'422':");
        assertThat(contract).contains("code: AUTHENTICATION_REQUIRED", "code: AUTHORIZATION_DENIED");
        assertThat(contract).contains("enum: [PASSED, FAILED]");

        var references = LOCAL_REFERENCE.matcher(contract).results()
                .map(result -> result.group(1))
                .distinct()
                .toList();
        assertThat(references).isNotEmpty();
        assertThat(references).allSatisfy(name ->
                assertThat(contract).contains("    " + name + ":"));
    }

    @Test
    void frozenPathsAndRequestShapeRemainCompatibleWithM3() throws IOException {
        Map<String, Object> contract = new Yaml().load(Files.readString(findContract()));
        assertThat(mapAt(contract, "info").get("version")).isEqualTo("1.0.0");
        assertThat(mapAt(contract, "paths")).containsOnlyKeys(
                "/api/v1/allergens", "/api/v1/label-versions/{labelVersionId}/validation-runs",
                "/api/v1/validation-runs/{validationRunId}");
        var request = mapAt(contract, "components", "schemas", "ValidationRunRequest");
        assertThat(request.get("additionalProperties")).isEqualTo(false);
        assertThat(request.get("required")).isEqualTo(java.util.List.of("ruleSetVersionId"));
        assertThat(mapAt(request, "properties")).containsOnlyKeys("ruleSetVersionId");
        assertThat(mapAt(request, "properties", "ruleSetVersionId").get("minLength")).isEqualTo(1);

        var create = mapAt(contract, "paths", "/api/v1/label-versions/{labelVersionId}/validation-runs", "post");
        assertThat(mapAt(create, "responses")).containsOnlyKeys("201", "400", "401", "403", "404", "409", "422", "500");
        assertThat(mapAt(create, "responses", "201", "content", "application/json", "schema").get("$ref"))
                .isEqualTo("#/components/schemas/ValidationRun");
        assertThat(mapAt(create, "requestBody", "content", "application/json", "schema").get("$ref"))
                .isEqualTo("#/components/schemas/ValidationRunRequest");
    }

    @Test
    void httpFieldsAndEnumsAgreeWithPortsAndM5WithoutExposingPersistenceFields() throws IOException {
        Map<String, Object> contract = new Yaml().load(Files.readString(findContract()));
        var schemas = mapAt(contract, "components", "schemas");
        assertThat(mapAt(schemas, "Allergen", "properties")).containsOnlyKeys(
                Arrays.stream(AllergenEntry.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        assertThat(mapAt(schemas, "ValidationResult", "properties")).containsOnlyKeys(
                Arrays.stream(ValidationFinding.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        assertThat(mapAt(schemas, "ValidationRun", "properties")).containsOnlyKeys(
                "validationRunId", "labelVersionId", "ruleSetVersionId", "status", "ranAt", "summary", "results");
        assertThat(mapAt(schemas, "ValidationRun", "properties", "status").get("enum"))
                .isEqualTo(Arrays.stream(ValidationStatus.values()).map(Enum::name).toList());
        assertThat(mapAt(schemas, "ValidationResult", "properties", "severity").get("enum"))
                .isEqualTo(Arrays.stream(ValidationSeverity.values()).map(Enum::name).toList());
        for (String flag : new String[]{"passed", "blocking"}) {
            assertThat(mapAt(schemas, "ValidationResult", "properties", flag).get("type")).isEqualTo("boolean");
        }
        assertThat(mapAt(schemas, "ValidationResult", "properties", "ruleDefinitionId").get("type"))
                .isEqualTo(java.util.List.of("string", "null"));
    }

    @Test
    void errorStatusCodesAndEnvelopeAreFrozenTogether() throws IOException {
        Map<String, Object> contract = new Yaml().load(Files.readString(findContract()));
        var error = mapAt(contract, "components", "schemas", "ApiError");
        assertThat(mapAt(error, "properties")).containsOnlyKeys("code", "message", "traceId", "evidenceId");
        assertThat(error.get("required")).isEqualTo(java.util.List.of("code", "message", "traceId", "evidenceId"));
        assertThat(error.get("additionalProperties")).isEqualTo(false);
        var responses = mapAt(contract, "paths", "/api/v1/label-versions/{labelVersionId}/validation-runs", "post", "responses");
        Map.of("400", "INVALID_REQUEST", "401", "AUTHENTICATION_REQUIRED", "403", "AUTHORIZATION_DENIED",
                "404", "RESOURCE_NOT_FOUND", "409", "LABEL_VERSION_NOT_CURRENT",
                "422", "VALIDATION_PRECONDITION_FAILED", "500", "INTERNAL_ERROR")
                .forEach((status, code) -> {
                    String reference = (String) mapAt(responses, status).get("$ref");
                    String name = reference.substring(reference.lastIndexOf('/') + 1);
                    var json = mapAt(contract, "components", "responses", name, "content", "application/json");
                    assertThat(mapAt(json, "schema").get("$ref")).isEqualTo("#/components/schemas/ApiError");
                    assertThat(mapAt(json, "example").get("code")).isEqualTo(code);
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapAt(Map<String, Object> root, String... path) {
        Object value = root;
        for (String part : path) {
            assertThat(value).isInstanceOf(Map.class);
            value = ((Map<String, Object>) value).get(part);
        }
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private Path findContract() {
        Path directory = Path.of("").toAbsolutePath();
        for (int level = 0; level < 3 && directory != null; level++) {
            Path candidate = directory.resolve("docs/contracts/allergen-validation-api-v1.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate the M2 OpenAPI contract");
    }
}
