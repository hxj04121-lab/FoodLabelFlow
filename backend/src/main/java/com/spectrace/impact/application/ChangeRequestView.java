package com.spectrace.impact.application;

import com.spectrace.impact.domain.ChangeRequest;

import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** A change request with the supplier material its specification versions belong to. */
public record ChangeRequestView(ChangeRequest changeRequest, String supplierMaterialId) {
    public ChangeRequestView {
        changeRequest = Objects.requireNonNull(changeRequest, "changeRequest");
        supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
    }
}
