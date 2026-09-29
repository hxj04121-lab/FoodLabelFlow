package com.spectrace.impact.application;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * A caller-safe impact boundary failure with the HTTP status and stable code from the
 * S3 impact error matrix, so the web adapter never rediscovers impact policy.
 */
public class ImpactFailure extends RuntimeException {
    private final int status;
    private final String code;

    public ImpactFailure(int status, String code, String message) {
        super(requiredText(message, "message"));
        if (status < 400 || status > 499) {
            throw new IllegalArgumentException("Impact failures are client errors; server faults stay exceptions");
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

    public static ImpactFailure invalid(String message) {
        return new ImpactFailure(400, "INVALID_REQUEST", message);
    }

    public static ImpactFailure notFound(String message) {
        return new ImpactFailure(404, "RESOURCE_NOT_FOUND", message);
    }

    public static ImpactFailure conflict(String message) {
        return new ImpactFailure(409, "DATA_CONFLICT", message);
    }

    public static ImpactFailure precondition(String code, String message) {
        return new ImpactFailure(422, code, message);
    }
}
