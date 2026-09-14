package com.spectrace.validation.application.port;

import com.spectrace.validation.application.contract.AllergenFact;
import com.spectrace.validation.application.contract.LabelValidationSnapshot;

import java.util.List;

/** Allergen module application port for deterministic derivation from a snapshot. */
public interface AllergenFactsPort {

    List<AllergenFact> deriveAllergenFacts(LabelValidationSnapshot snapshot);
}
