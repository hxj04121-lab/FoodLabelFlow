package com.spectrace.label.interfaces.web;

import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.shared.api.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelErrorsTest {

    private final LabelErrors errors = new LabelErrors();

    @Test
    void mapsLabelVersionConflictToHttp409() {
        LabelVersionConflictException exception =
                new LabelVersionConflictException(
                        "Label version is stale or historical: label_old"
                );

        ResponseEntity<ApiError> response =
                errors.conflict(exception);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "LABEL_VERSION_CONFLICT",
                response.getBody().code()
        );
    }
}