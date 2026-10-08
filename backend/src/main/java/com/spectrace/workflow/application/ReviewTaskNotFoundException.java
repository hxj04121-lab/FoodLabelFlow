package com.spectrace.workflow.application;

public class ReviewTaskNotFoundException extends RuntimeException {
    public ReviewTaskNotFoundException(String taskId) {
        super("ReviewTask was not found: " + taskId);
    }
}
