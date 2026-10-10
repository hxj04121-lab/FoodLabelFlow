package com.spectrace;

import com.spectrace.identity.application.CurrentIdentityView;
import com.spectrace.identity.interfaces.web.CurrentIdentityController.DemoIdentityOption;
import com.spectrace.impact.interfaces.web.ImpactAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class S4M2ContractAlignmentTest {

    private static final String LABEL_CONTRACT = "docs/contracts/s3-label-product-flow-api-v1.yaml";
    private static final String IMPACT_CONTRACT = "docs/contracts/s3-impact-review-publication-api-v1.yaml";

    @Test
    void mergedRevisionAndDemoIdentityEndpointsAreDescribedForTheirConsumers() throws IOException {
        Map<String, Object> contract = load(LABEL_CONTRACT);

        assertThat(at(contract, "info").get("version")).isEqualTo("1.2.0");
        assertThat(contract.get("x-spec-trace-contract-state"))
                .isEqualTo("CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE");
        var paths = at(contract, "paths");
        assertThat(paths).containsKeys(
                "/api/review-tasks/{reviewTaskId}/draft-revisions",
                "/api/identity/current",
                "/api/identity/demo-options");

        var revision = at(paths, "/api/review-tasks/{reviewTaskId}/draft-revisions");
        assertThat(revision).containsOnlyKeys("post");
        assertThat(valueAt(revision, "post", "x-required-permission")).isEqualTo("LABEL.CREATE");
        assertThat(valueAt(revision, "post", "x-adoption-status").toString())
                .contains("Implemented on main through PR 88")
                .contains("M4 workflow review and M3 consumer acceptance remain separately pending");
        assertThat(valueAt(revision, "post", "requestBody", "required")).isEqualTo(true);
        assertThat(valueAt(revision, "post", "requestBody", "content", "application/json", "schema", "$ref"))
                .isEqualTo("#/components/schemas/ReturnedDraftRevision");
        assertThat(at(revision, "post", "responses")).containsKeys("201", "400", "401", "403", "404", "409", "500");

        var options = at(paths, "/api/identity/demo-options", "get");
        assertThat(options.get("operationId")).isEqualTo("getDemoIdentityOptions");
        assertThat(options.get("security")).isEqualTo(List.of());
        assertThat(options).doesNotContainKey("x-required-permission");
        assertThat(at(options, "responses")).containsOnlyKeys("200", "401", "500");
        assertThat(valueAt(options, "responses", "200", "content", "application/json", "schema", "items", "$ref"))
                .isEqualTo("#/components/schemas/DemoIdentityOption");

        var schemas = at(contract, "components", "schemas");
        assertThat(at(schemas, "DemoIdentityOption", "properties")).containsOnlyKeys("key", "subject", "actor");
        assertThat(valueAt(schemas, "DemoIdentityOption", "required"))
                .isEqualTo(List.of("key", "subject", "actor"));
        assertThat(valueAt(schemas, "DemoIdentityOption", "properties", "key", "enum"))
                .isEqualTo(List.of("MAKER", "CHECKER", "PUBLISHER"));
        assertThat(valueAt(schemas, "DemoIdentityOption", "properties", "actor", "$ref"))
                .isEqualTo("#/components/schemas/CurrentIdentity");
        assertThat(at(schemas, "CurrentIdentity", "properties")).containsOnlyKeys(
                Arrays.stream(CurrentIdentityView.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        assertThat(at(schemas, "DemoIdentityOption", "properties")).containsOnlyKeys(
                Arrays.stream(DemoIdentityOption.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));

        String matrix = Files.readString(repositoryRoot().resolve("docs/contracts/s3-label-product-flow-error-matrix-v1.md"));
        assertThat(matrix).contains("`POST /api/review-tasks/{id}/draft-revisions`",
                "`GET /api/identity/demo-options`", "Implemented on main through PR #88");
    }

    @Test
    void impactContractMatchesTheExistingM1ResponseWithoutDuplicatingItsReadModel() throws IOException {
        Map<String, Object> contract = load(IMPACT_CONTRACT);
        var schemas = at(contract, "components", "schemas");

        assertThat(at(schemas, "ImpactAnalysis", "properties")).containsOnlyKeys(
                Arrays.stream(ImpactAnalysisResponse.class.getRecordComponents())
                        .map(c -> c.getName()).toArray(String[]::new));
        assertThat(at(schemas, "ImpactAnalysis", "properties")).containsKeys(
                "relevantProductCount", "noActionCount", "reviewRequiredCount", "findings");
        assertThat(valueAt(schemas, "ReviewRequiredFinding", "properties", "outcome", "const"))
                .isEqualTo("REVIEW_REQUIRED");
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
}
