package com.spectrace.catalog.interfaces.web;

import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.shared.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrors {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogErrors.class);
    @ExceptionHandler(CatalogFailure.class)
    public ResponseEntity<ApiError> business(CatalogFailure error) {
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(ApiError.of(
                "DATA_CONFLICT", "Duplicate business key or invalid reference"));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalid(Exception error) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_REQUEST", "Request body is missing or invalid"));
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> internal(RuntimeException error) {
        LOG.error("Catalog API request failed", error);
        return ResponseEntity.internalServerError().body(ApiError.of(
                "INTERNAL_ERROR", "The catalog request could not be completed"));
    }
}
