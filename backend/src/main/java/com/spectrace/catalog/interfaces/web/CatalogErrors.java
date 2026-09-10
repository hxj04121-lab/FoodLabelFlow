package com.spectrace.catalog.interfaces.web;

import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.shared.api.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrors {
    @ExceptionHandler(CatalogFailure.class)
    public ResponseEntity<ApiError> business(CatalogFailure error) {
        return ResponseEntity.status(error.status()).body(ApiError.of(error.code(), error.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(ApiError.of(
                "DATA_CONFLICT", "Duplicate business key or invalid reference"));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalid(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_REQUEST", "Request body is missing or invalid"));
    }
}
