package com.spectrace.impact.interfaces.web;

import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.shared.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error envelope for the impact API; identity failures are mapped by IdentityErrors first. */
@RestControllerAdvice(assignableTypes = ChangeRequestController.class)
public class ImpactErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ImpactErrors.class);

    @ExceptionHandler(ImpactFailure.class)
    public ResponseEntity<ApiError> impactFailure(ImpactFailure error) {
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidRequest(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "The request is missing or invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internal(Exception error) {
        LOG.error("Impact API request failed", error);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_ERROR", "The change request could not be completed"));
    }
}
