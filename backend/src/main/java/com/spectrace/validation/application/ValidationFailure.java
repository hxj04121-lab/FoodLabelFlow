package com.spectrace.validation.application;

public class ValidationFailure extends RuntimeException {

    private final int status;
    private final String code;

    public ValidationFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
