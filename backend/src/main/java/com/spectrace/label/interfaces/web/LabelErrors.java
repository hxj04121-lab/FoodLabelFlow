package com.spectrace.label.interfaces.web;

import com.spectrace.label.application.LabelDraftNotFoundException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.shared.api.ApiError;
import com.spectrace.shared.api.InvalidCommandException;
import com.spectrace.label.application.InvalidLabelDraftRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1)
public class LabelErrors {
    @ExceptionHandler(InvalidLabelDraftRequestException.class)
    public ResponseEntity<ApiError> invalidDraft(InvalidLabelDraftRequestException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_DRAFT_INVALID", error.getMessage()));
    }

    @ExceptionHandler(InvalidCommandException.class)
    public ResponseEntity<ApiError> invalidCommand(InvalidCommandException error) {
        return ResponseEntity.badRequest().body(ApiError.of("LABEL_COMMAND_INVALID", error.getMessage()));
    }


    @ExceptionHandler(LabelDraftNotFoundException.class)
    public ResponseEntity<ApiError> notFound(
            LabelDraftNotFoundException error
    ) {
        return ResponseEntity.status(404)
                .body(ApiError.of(
                        "LABEL_NOT_FOUND",
                        error.getMessage()
                ));
    }

    @ExceptionHandler(LabelVersionConflictException.class)
    public ResponseEntity<ApiError> conflict(
            LabelVersionConflictException error
    ) {
        return ResponseEntity.status(409)
                .body(ApiError.of(
                        "LABEL_VERSION_CONFLICT",
                        error.getMessage()
                ));
    }
}
