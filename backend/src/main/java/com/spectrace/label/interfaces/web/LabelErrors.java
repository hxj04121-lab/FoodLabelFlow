package com.spectrace.label.interfaces.web;

import com.spectrace.label.application.LabelDraftNotFoundException;
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
}