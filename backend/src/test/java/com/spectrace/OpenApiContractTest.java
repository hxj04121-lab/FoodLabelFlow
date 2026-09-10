package com.spectrace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
