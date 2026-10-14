package com.spectrace.audit.interfaces.web;

import com.spectrace.audit.application.AuditHistoryFailure;
import com.spectrace.shared.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductLabelHistoryController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProductLabelHistoryErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ProductLabelHistoryErrors.class);

    @ExceptionHandler(AuditHistoryFailure.class)
    public ResponseEntity<ApiError> failure(AuditHistoryFailure error) {
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        LOG.error("Product label history read failed", error);
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "The label history could not be read"));
    }
}
