package com.spectrace;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class S3ImpactApiContractTest {

    private static final String CONTRACT = "docs/contracts/s3-impact-review-publication-api-v1.yaml";
    private static final String S2_CONTRACT = "docs/contracts/allergen-validation-api-v1.yaml";

    @Test
    void versionedCandidateDefinesOnlyM2OwnedImpactRoutes() throws IOException {
        Map<String, Object> contract = load(CONTRACT);

        assertThat(contract.get("openapi")).isEqualTo("3.1.0");
        assertThat(at(contract, "info").get("version")).isEqualTo("1.0.0");
        assertThat(contract.get("x-spec-trace-contract-state"))
                .isEqualTo("CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE");
        assertThat(at(contract, "paths")).containsOnlyKeys(
                "/api/v1/change-requests",
                "/api/v1/change-requests/{changeRequestId}",
                "/api/v1/change-requests/{changeRequestId}/impact-analyses",
                "/api/v1/impact-analyses/{impactAnalysisId}");
        assertThat(at(contract, "paths", "/api/v1/change-requests")).containsOnlyKeys("post");
        assertThat(at(contract, "paths", "/api/v1/change-requests/{changeRequestId}")).containsOnlyKeys("get");
        assertThat(at(contract, "paths", "/api/v1/change-requests/{changeRequestId}/impact-analyses"))
                .containsOnlyKeys("post");
        assertThat(at(contract, "paths", "/api/v1/impact-analyses/{impactAnalysisId}")).containsOnlyKeys("get");
        assertThat(at(contract, "paths", "/api/v1/change-requests", "post", "responses"))
                .containsKey("201");
        assertThat(at(contract, "paths", "/api/v1/change-requests/{changeRequestId}/impact-analyses", "post", "responses"))
                .containsKeys("200", "201");
        assertThat(at(contract, "paths", "/api/v1/change-requests", "post", "responses"))
                .containsOnlyKeys("201", "400", "401", "403", "404", "409", "422", "500", "503");
        assertThat(at(contract, "paths", "/api/v1/change-requests/{changeRequestId}", "get", "responses"))
                .containsOnlyKeys("200", "401", "403", "404", "500", "503");
        assertThat(at(contract, "paths", "/api/v1/change-requests/{changeRequestId}/impact-analyses", "post", "responses"))
                .containsOnlyKeys("200", "201", "400", "401", "403", "404", "409", "422", "500", "503");
        assertThat(valueAt(contract, "paths", "/api/v1/change-requests/{changeRequestId}/impact-analyses",
                "post", "requestBody", "required")).isEqualTo(true);
        assertThat(valueAt(contract, "paths", "/api/v1/change-requests/{changeRequestId}/impact-analyses",
                "post", "requestBody", "content", "application/json", "schema", "$ref"))
                .isEqualTo("#/components/schemas/ImpactAnalysisTriggerRequest");
        assertThat(at(contract, "paths", "/api/v1/impact-analyses/{impactAnalysisId}", "get", "responses"))
                .containsOnlyKeys("200", "401", "403", "404", "500", "503");
    }

    @Test
    void changeAndImpactSchemasCarryStableVersionAndReviewHandoffReferences() throws IOException {
        Map<String, Object> contract = load(CONTRACT);
        var schemas = at(contract, "components", "schemas");

        var create = at(schemas, "ChangeRequestCreate");
        assertThat(create.get("additionalProperties")).isEqualTo(false);
        assertThat(create.get("required")).isEqualTo(List.of(
                "changeType", "supplierMaterialId", "previousSpecificationVersionId", "targetSpecificationVersionId"));
        assertThat(valueAt(create, "properties", "changeType", "enum")).isEqualTo(List.of("INGREDIENT_SPEC"));

        assertThat(valueAt(schemas, "ImpactAnalysisTriggerRequest", "required"))
                .isEqualTo(List.of("ruleSetVersionId"));
        assertThat(valueAt(schemas, "ChangeRequest", "properties", "status", "enum"))
                .isEqualTo(List.of("SUBMITTED", "ANALYZED", "COMPLETED"));

        var analysis = at(schemas, "ImpactAnalysis");
        assertThat(valueAt(analysis, "properties", "status", "enum")).isEqualTo(List.of("COMPLETED"));
        assertThat(valueAt(analysis, "properties", "findings", "items", "$ref"))
                .isEqualTo("#/components/schemas/ImpactFinding");
        assertThat(at(schemas, "ImpactFinding", "oneOf")).hasSize(2);
        assertThat(at(schemas, "NoActionFinding", "properties")).doesNotContainKey("reviewTask");
        assertThat((List<?>) valueAt(schemas, "NoActionFinding", "required"))
                .contains("proposedFormulaVersionId");
        assertThat(valueAt(schemas, "NoActionFinding", "properties", "outcome", "const")).isEqualTo("NO_ACTION");
        assertThat((List<?>) valueAt(schemas, "ReviewRequiredFinding", "required"))
                .contains("reviewTask", "proposedFormulaVersionId");
        assertThat(valueAt(schemas, "ReviewRequiredFinding", "properties", "outcome", "const"))
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(valueAt(schemas, "ReviewTaskHandoff", "properties", "draftLabelVersionId", "type"))
                .isEqualTo(List.of("string", "null"));
        assertThat(at(schemas, "ReviewTaskHandoff", "properties")).containsOnlyKeys(
                "reviewTaskId", "impactFindingId", "productId", "currentFormulaVersionId",
                "currentLabelVersionId", "draftLabelVersionId");
        assertThat(at(schemas, "PublicationHandoff", "properties")).containsKeys(
                "reviewTaskId", "sourceLabelVersionId", "publishedLabelVersionId",
                "supersededLabelVersionId", "validationRunId", "publicationRecordId", "publishedAt");
        assertThat(at(schemas, "PublicationHandoff", "properties")).doesNotContainKeys(
                "status", "reviewDecision", "permission", "databaseId");
    }

    @Test
    void candidateErrorsReuseTheFrozenS2EnvelopeAndPreserveItsContract() throws IOException {
        Map<String, Object> candidate = load(CONTRACT);
        Map<String, Object> s2 = load(S2_CONTRACT);
        var s2Error = at(s2, "components", "schemas", "ApiError");

        assertThat(at(s2, "info").get("version")).isEqualTo("1.0.0");
        assertThat(at(s2, "paths")).containsOnlyKeys(
                "/api/v1/allergens",
                "/api/v1/label-versions/{labelVersionId}/validation-runs",
                "/api/v1/validation-runs/{validationRunId}");
        assertThat(at(s2Error, "properties")).containsOnlyKeys("code", "message", "traceId", "evidenceId");
        assertThat(s2Error.get("required")).isEqualTo(List.of("code", "message", "traceId", "evidenceId"));
        assertThat(s2Error.get("additionalProperties")).isEqualTo(false);
        assertThat(valueAt(candidate, "components", "schemas", "ApiError", "$ref"))
                .isEqualTo("./allergen-validation-api-v1.yaml#/components/schemas/ApiError");

        var responses = at(candidate, "components", "responses");
        assertThat(responses).containsOnlyKeys(
                "InvalidRequest", "AuthenticationRequired", "AuthorizationDenied", "ResourceNotFound",
                "DataConflict", "DomainPreconditionFailed", "InternalError", "DependencyUnavailable");
        responses.forEach((name, value) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) value;
            var schema = at(response, "content", "application/json", "schema");
            assertThat(schema.get("$ref"))
                    .as("response %s must use the frozen S2 ApiError schema", name)
                    .isEqualTo("./allergen-validation-api-v1.yaml#/components/schemas/ApiError");
        });
        assertThat(valueAt(candidate, "components", "responses", "DependencyUnavailable",
                "content", "application/json", "example", "code")).isEqualTo("SERVICE_UNAVAILABLE");
        Path root = repositoryRoot();
        String matrix = Files.readString(root.resolve("docs/contracts/s3-impact-api-error-matrix-v1.md"));
        Map.of("400", List.of("INVALID_REQUEST"), "401", List.of("AUTHENTICATION_REQUIRED"),
                "403", List.of("AUTHORIZATION_DENIED"), "404", List.of("RESOURCE_NOT_FOUND"),
                "409", List.of("CURRENT_FORMULA_CHANGED", "DATA_CONFLICT"),
                "422", List.of("SPECIFICATION_MATERIAL_MISMATCH", "SPECIFICATION_NOT_RELEASED",
                        "SPECIFICATION_NOT_EFFECTIVE"),
                "500", List.of("INTERNAL_ERROR"), "503", List.of("CATALOG_INTEGRATION_UNAVAILABLE"))
                .forEach((status, codes) -> codes.forEach(code ->
                        assertThat(matrix).contains("| " + status + " | `" + code + "` |")));
        assertAllReferencesResolve(candidate, s2);
    }

    @SuppressWarnings("unchecked")
    private static Object valueAt(Map<String, Object> root, String... path) {
        Object value = root;
        for (String part : path) {
            assertThat(value).as("path %s", String.join(".", path)).isInstanceOf(Map.class);
            value = ((Map<String, Object>) value).get(part);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> root, String... path) {
        Object value = valueAt(root, path);
        assertThat(value).as("path %s", String.join(".", path)).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> load(String relativePath) throws IOException {
        return new Yaml().load(Files.readString(repositoryRoot().resolve(relativePath)));
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("docs/contracts"))) {
            root = root.getParent();
        }
        assertThat(root).as("repository root containing docs/contracts").isNotNull();
        return root;
    }

    private static void assertAllReferencesResolve(Map<String, Object> candidate, Map<String, Object> s2) {
        List<String> references = new ArrayList<>();
        collectReferences(candidate, references);
        assertThat(references).isNotEmpty();
        for (String reference : references) {
            if (reference.startsWith("#/")) {
                assertThat(valueAt(candidate, reference.substring(2).split("/")))
                        .as("local reference %s", reference).isNotNull();
            } else if (reference.startsWith("./allergen-validation-api-v1.yaml#/")) {
                assertThat(valueAt(s2, reference.substring(reference.indexOf("#/") + 2).split("/")))
                        .as("S2 compatibility reference %s", reference).isNotNull();
            } else {
                throw new AssertionError("Unexpected contract reference: " + reference);
            }
        }
    }

    private static void collectReferences(Object value, List<String> references) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                if ("$ref".equals(key) && nested instanceof String reference) {
                    references.add(reference);
                } else {
                    collectReferences(nested, references);
                }
            });
        } else if (value instanceof Iterable<?> values) {
            values.forEach(nested -> collectReferences(nested, references));
        }
    }
}
