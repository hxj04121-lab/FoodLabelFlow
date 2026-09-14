package com.spectrace.shared.contract;

/** Common value checks for immutable, internal application contracts. */
public final class ContractValues {
    private ContractValues() {
    }

    public static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
