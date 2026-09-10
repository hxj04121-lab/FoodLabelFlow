package com.spectrace.identity.application;

public class UnknownIdentityException extends RuntimeException {

    public UnknownIdentityException(String message) {
        super(message);
    }
}