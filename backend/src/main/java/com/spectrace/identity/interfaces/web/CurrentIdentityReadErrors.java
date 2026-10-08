package com.spectrace.identity.interfaces.web;

import com.spectrace.shared.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CurrentIdentityController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class CurrentIdentityReadErrors {
    private static final Logger LOG = LoggerFactory.getLogger(CurrentIdentityReadErrors.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        LOG.error("Current identity read failed", error);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_ERROR", "The current identity could not be read"));
    }
}
