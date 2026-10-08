package com.spectrace.shared.api;


import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/** Local strict command parsing; does not change JSON behavior of unrelated modules. */
public final class StrictCommandJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private StrictCommandJson() {}

    public static JsonNode object(String body, Set<String> required, Set<String> optional) {
        JsonNode input;
        try {
            input = JSON.readTree(body);
        } catch (JacksonException error) {
            throw new InvalidCommandException("Command must be a JSON object with no duplicate fields or trailing content");
        }
        if (input == null || !input.isObject()) {
            throw new InvalidCommandException("Command must be a JSON object");
        }
        checkFields(input, required, optional);
        return input;
    }

    public static void checkFields(JsonNode input, Set<String> required, Set<String> optional) {
        if (!input.isObject() || required.stream().anyMatch(field -> !input.has(field))
                || input.properties().stream().anyMatch(field ->
                !required.contains(field.getKey()) && !optional.contains(field.getKey()))) {
            throw new InvalidCommandException("Command fields do not match the resource contract");
        }
    }

    public static String text(JsonNode input, String field, int maxLength) {
        JsonNode value = input.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()
                || value.stringValue().length() > maxLength
                || !value.stringValue().equals(value.stringValue().trim())) {
            throw new InvalidCommandException(field + " must be a non-blank string of at most " + maxLength + " characters");
        }
        return value.stringValue();
    }
}
