package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.shared.api.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** HTTP mapping for the identity boundary; authentication and authorization stay distinct. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityErrors {

    @ExceptionHandler(UnknownIdentityException.class)
    public ResponseEntity<ApiError> unauthenticated(UnknownIdentityException error) {
        return ResponseEntity.status(401)
                .body(ApiError.of("AUTHENTICATION_REQUIRED", error.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AuthorizationDeniedException error) {
        return ResponseEntity.status(403)
                .body(ApiError.of("AUTHORIZATION_DENIED", error.getMessage()));
    }
}
