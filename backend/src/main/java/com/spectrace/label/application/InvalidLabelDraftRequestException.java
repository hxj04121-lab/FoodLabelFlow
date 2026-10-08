package com.spectrace.label.application;

public class InvalidLabelDraftRequestException extends IllegalArgumentException {
    public InvalidLabelDraftRequestException(String message) {
        super(message);
    }
}
