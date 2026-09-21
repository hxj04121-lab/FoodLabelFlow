package com.spectrace.validation.interfaces.web;

import com.spectrace.shared.api.ApiError;
import com.spectrace.validation.application.ValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error envelope for this API; unexpected failures remain errors and are logged server-side. */
@RestControllerAdvice(assignableTypes = ValidationController.class)
public class ValidationErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationErrors.class);

    @ExceptionHandler(ValidationFailure.class)
    public ResponseEntity<ApiError> validationFailure(ValidationFailure error) {
        if (error.status() >= 500) {
            return internal(error);
        }
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "The request is missing or invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internal(Exception error) {
        LOG.error("Validation API request failed", error);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_ERROR", "The validation request could not be completed"));
    }
}
