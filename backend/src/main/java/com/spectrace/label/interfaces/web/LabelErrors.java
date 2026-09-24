package com.spectrace.label.interfaces.web;

import com.spectrace.label.application.LabelDraftNotFoundException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.shared.api.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LabelErrors {

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
