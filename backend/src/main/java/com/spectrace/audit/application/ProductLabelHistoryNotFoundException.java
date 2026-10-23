package com.spectrace.audit.application;

public class ProductLabelHistoryNotFoundException extends RuntimeException {
    public ProductLabelHistoryNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
