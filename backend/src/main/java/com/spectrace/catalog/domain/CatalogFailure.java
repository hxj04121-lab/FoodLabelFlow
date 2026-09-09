package com.spectrace.catalog.domain;

public class CatalogFailure extends RuntimeException {
    private final int status;
    private final String code;

    public CatalogFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static CatalogFailure invalid(String message) {
        return new CatalogFailure(400, "INVALID_REQUEST", message);
    }
}
