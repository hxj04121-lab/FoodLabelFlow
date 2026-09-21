package com.spectrace.validation.interfaces.web;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.shared.api.ApiError;
import com.spectrace.validation.application.ValidationFailure;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(0)
@RestControllerAdvice(assignableTypes = {
        ValidationController.class,
        com.spectrace.allergen.interfaces.web.AllergenController.class
})
public class ValidationApiErrors {
    @ExceptionHandler(ValidationFailure.class)
    public ResponseEntity<ApiError> validation(ValidationFailure error) {
        String message = "INTERNAL_ERROR".equals(error.code())
                ? "An unexpected error occurred."
                : error.getMessage();
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), message));
    }

    @ExceptionHandler(UnknownIdentityException.class)
    public ResponseEntity<ApiError> unauthenticated(UnknownIdentityException ignored) {
        return ResponseEntity.status(401).body(ApiError.of(
                "AUTHENTICATION_REQUIRED", "Authentication is required."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AuthorizationDeniedException ignored) {
        return ResponseEntity.status(403).body(ApiError.of(
                "AUTHORIZATION_DENIED", "The actor is not permitted to access this resource."));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_REQUEST", "Request is missing or invalid."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> dataAccess(DataAccessException ignored) {
        return internal();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ignored) {
        return internal();
    }

    private ResponseEntity<ApiError> internal() {
        return ResponseEntity.internalServerError().body(ApiError.of(
                "INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
