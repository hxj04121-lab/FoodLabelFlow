package com.spectrace.validation.interfaces.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration
class ValidationJsonConfiguration {
    @Bean
    JsonMapperBuilderCustomizer rejectUnknownRequestFields() {
        return builder -> builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }
}
