package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDenied(
            AuthorizationDeniedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "code", "AUTHORIZATION_DENIED",
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(UnknownIdentityException.class)
    public ResponseEntity<Map<String, String>> handleUnknownIdentity(
            UnknownIdentityException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "code", "UNKNOWN_IDENTITY",
                        "message", exception.getMessage()
                ));
    }
}