package com.spectrace.label.application;

public class LabelDraftNotFoundException extends RuntimeException {

    public LabelDraftNotFoundException(String labelVersionId) {
        super("Label version not found: " + labelVersionId);
    }
}