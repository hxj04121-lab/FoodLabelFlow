package com.spectrace;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.validation.application.LabelAllergenFacts;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LabelAllergenApiContractTest {
    @Test
    void additiveContractMatchesExactResourceAndExistingErrorEnvelope() throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("docs/contracts"))) {
            directory = directory.getParent();
        }
        assertThat(directory).isNotNull();
        var yaml = new Yaml();
        Map<String, Object> api = yaml.load(Files.readString(directory.resolve("docs/contracts/label-derived-allergens-api-v1.yaml")));
        Map<String, Object> frozen = yaml.load(Files.readString(directory.resolve("docs/contracts/allergen-validation-api-v1.yaml")));
        assertThat(api.get("openapi")).isEqualTo("3.1.0");
        assertThat(at(api, "info").get("version")).isEqualTo("1.0.0");
        String route = "/api/v1/label-versions/{labelVersionId}/derived-allergens";
        assertThat(at(api, "paths")).containsOnlyKeys(route);
        assertThat(at(api, "paths", route)).containsOnlyKeys("get");
        var get = at(api, "paths", route, "get");
        assertThat(get).doesNotContainKey("requestBody");
        assertThat(at(get, "responses")).containsOnlyKeys("200", "400", "401", "404", "422", "500");
        for (String status : List.of("400", "401", "404", "422", "500")) {
            String ref = (String) at(get, "responses", status).get("$ref");
            assertThat(ref).startsWith("./allergen-validation-api-v1.yaml#/components/responses/");
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            assertThat(at(frozen, "components", "responses", name, "content", "application/json", "schema").get("$ref"))
                    .isEqualTo("#/components/schemas/ApiError");
        }
        var schemas = at(api, "components", "schemas");
        Map.of("LabelAllergenFacts", LabelAllergenFacts.class, "AllergenFact", AllergenFact.class,
                "DerivationEvidence", AllergenFact.DerivationEvidence.class,
                "UnresolvedComponent", AllergenDerivation.UnresolvedComponent.class).forEach((name, type) -> {
            var fields = Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
            var schema = at(schemas, name);
            assertThat(schema.get("additionalProperties")).isEqualTo(false);
            assertThat(schema.get("required")).isEqualTo(fields);
            assertThat(at(schema, "properties")).containsOnlyKeys(fields.toArray(String[]::new));
        });
        assertThat(at(schemas, "UnresolvedComponent", "properties", "matchStatus").get("enum"))
                .isEqualTo(List.of("UNMAPPED", "AMBIGUOUS"));
        assertThat(at(schemas, "AllergenFact", "properties", "derivationEvidence").get("minItems")).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (String part : path) current = (Map<String, Object>) current.get(part);
        return current;
    }
}
