package com.spectrace.impact.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

final class ImpactJsonMapping {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private ImpactJsonMapping() {
    }

    static String writeStrings(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JacksonException error) {
            throw new IllegalStateException("Unable to serialize impact JSON values", error);
        }
    }

    static List<String> readStrings(String value) {
        try {
            return List.copyOf(MAPPER.readValue(value, STRING_LIST));
        } catch (JacksonException | NullPointerException error) {
            throw new IllegalStateException("Unable to deserialize impact JSON values", error);
        }
    }
}
