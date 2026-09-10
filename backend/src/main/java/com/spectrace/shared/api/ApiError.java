package com.spectrace.shared.api;

/**
 * Canonical HTTP error representation shared by module web adapters.
 *
 * <p>The code is stable and machine-readable. Trace and evidence identifiers are
 * nullable because they must only be returned when a real identifier exists.</p>
 */
public record ApiError(
        String code,
        String message,
        String traceId,
        String evidenceId
) {
    public ApiError {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Error code is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Error message is required");
        }
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, null);
    }
}
