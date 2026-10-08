package com.spectrace.workflow.interfaces.web;

import com.spectrace.label.application.InvalidLabelDraftRequestException;
import com.spectrace.shared.api.ApiError;
import com.spectrace.workflow.application.ReviewTaskNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped mappings for expected workflow command failures; database failures remain visible. */
@RestControllerAdvice(assignableTypes = LabelWorkflowController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1)
public class WorkflowErrors {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> missingBody(org.springframework.http.converter.HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_COMMAND_INVALID", "A JSON command body is required"));
    }

    @ExceptionHandler(InvalidLabelDraftRequestException.class)
    public ResponseEntity<ApiError> invalid(InvalidLabelDraftRequestException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_COMMAND_INVALID", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidDecision(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_COMMAND_INVALID", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(ApiError.of("LABEL_WORKFLOW_CONFLICT", error.getMessage()));
    }

    @ExceptionHandler(ReviewTaskNotFoundException.class)
    public ResponseEntity<ApiError> missing(ReviewTaskNotFoundException error) {
        return ResponseEntity.status(404).body(ApiError.of("REVIEW_TASK_NOT_FOUND", error.getMessage()));
    }
}
