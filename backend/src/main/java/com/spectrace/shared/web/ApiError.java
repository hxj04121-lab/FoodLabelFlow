package com.spectrace.shared.web;

public record ApiError(
        String code,
        String message,
        String traceId,
        String evidenceId
) {
}