package com.spectrace.catalog.interfaces.web;

import com.spectrace.catalog.domain.CatalogFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrors {
    @ExceptionHandler(CatalogFailure.class)
    public ResponseEntity<Map<String, String>> business(CatalogFailure error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(Map.of("code", "DATA_CONFLICT", "message", "Duplicate business key or invalid reference"));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalid(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", "Request body is missing or invalid"));
    }
}
