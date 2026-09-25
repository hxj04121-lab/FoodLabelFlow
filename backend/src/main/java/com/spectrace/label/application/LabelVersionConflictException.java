package com.spectrace.label.application;

public class LabelVersionConflictException extends RuntimeException {

    public LabelVersionConflictException(String message) {
        super(message);
    }
}