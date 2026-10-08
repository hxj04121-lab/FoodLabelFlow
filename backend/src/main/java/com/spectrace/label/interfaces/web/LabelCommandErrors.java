package com.spectrace.label.interfaces.web;

import com.spectrace.shared.api.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LabelDraftController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1)
public class LabelCommandErrors {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> missingBody(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_COMMAND_INVALID", "A JSON command body is required"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(ApiError.of("LABEL_WORKFLOW_CONFLICT", error.getMessage()));
    }
}
