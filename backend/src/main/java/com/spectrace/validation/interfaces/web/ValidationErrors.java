package com.spectrace.validation.interfaces.web;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.shared.api.ApiError;
import com.spectrace.validation.application.ValidationFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ValidationController.class)
public class ValidationErrors {

    @ExceptionHandler(ValidationFailure.class)
    public ResponseEntity<ApiError> validation(ValidationFailure error) {
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }

    @ExceptionHandler(UnknownIdentityException.class)
    public ResponseEntity<ApiError> unauthenticated(UnknownIdentityException error) {
        return ResponseEntity.status(401).body(ApiError.of("AUTHENTICATION_REQUIRED", error.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AuthorizationDeniedException error) {
        return ResponseEntity.status(403).body(ApiError.of("AUTHORIZATION_DENIED", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalid(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_REQUEST", "Request body is missing or invalid"));
    }
}
