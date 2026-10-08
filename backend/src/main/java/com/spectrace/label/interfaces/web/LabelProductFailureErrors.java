package com.spectrace.label.interfaces.web;

import com.spectrace.shared.api.ApiError;
import com.spectrace.workflow.interfaces.web.LabelWorkflowController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Fallback after expected module/auth mappings; never report a failed transaction as success. */
@RestControllerAdvice(assignableTypes = {LabelDraftController.class, LabelWorkflowController.class})
@Order(Ordered.LOWEST_PRECEDENCE)
public class LabelProductFailureErrors {
    private static final Logger LOG = LoggerFactory.getLogger(LabelProductFailureErrors.class);

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> contentType(org.springframework.web.HttpMediaTypeNotSupportedException error) {
        return ResponseEntity.status(415).body(ApiError.of("LABEL_CONTENT_TYPE_UNSUPPORTED", "This command requires application/json"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(org.springframework.web.HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(405).body(ApiError.of("LABEL_METHOD_NOT_ALLOWED", "This resource does not support the requested method"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        LOG.error("Label product command failed", error);
        return ResponseEntity.status(500).body(ApiError.of("INTERNAL_ERROR", "The label command could not be completed"));
    }
}
