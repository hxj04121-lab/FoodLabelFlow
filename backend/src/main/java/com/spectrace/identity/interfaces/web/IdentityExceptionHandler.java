package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.shared.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(
            AuthorizationDeniedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(
                        "AUTHORIZATION_DENIED",
                        exception.getMessage(),
                        null,
                        null
                ));
    }

    @ExceptionHandler(UnknownIdentityException.class)
    public ResponseEntity<ApiError> handleUnknownIdentity(
            UnknownIdentityException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(
                        "AUTHORIZATION_DENIED",
                        exception.getMessage(),
                        null,
                        null
                ));
    }
}