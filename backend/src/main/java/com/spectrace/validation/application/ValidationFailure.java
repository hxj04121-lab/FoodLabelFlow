package com.spectrace.validation.application;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * A caller-safe validation boundary failure. Web adapters can map the status and
 * code directly without rediscovering validation policy in another layer.
 */
public class ValidationFailure extends RuntimeException {
    private final int status;
    private final String code;

    public ValidationFailure(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ValidationFailure(int status, String code, String message, Throwable cause) {
        super(requiredText(message, "message"), cause);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("Validation failure status must be an HTTP error status");
        }
        this.status = status;
        this.code = requiredText(code, "code");
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ValidationFailure invalid(String message) {
        return new ValidationFailure(400, "INVALID_REQUEST", message);
    }

    public static ValidationFailure notFound(String message) {
        return new ValidationFailure(404, "RESOURCE_NOT_FOUND", message);
    }

    public static ValidationFailure notCurrent(String message) {
        return new ValidationFailure(409, "LABEL_VERSION_NOT_CURRENT", message);
    }

    public static ValidationFailure precondition(String message) {
        return new ValidationFailure(422, "VALIDATION_PRECONDITION_FAILED", message);
    }

    public static ValidationFailure internal(String message, Throwable cause) {
        return new ValidationFailure(500, "INTERNAL_ERROR", message, cause);
    }
}
