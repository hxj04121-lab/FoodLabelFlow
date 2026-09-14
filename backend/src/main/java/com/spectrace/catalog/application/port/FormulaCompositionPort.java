package com.spectrace.catalog.application.port;

import java.util.Optional;

/** Catalog-owned input to relational allergen derivation (BR-03). */
public interface FormulaCompositionPort {
    /**
     * Return every item and its exact specification components, ordered by sequence
     * then ID. Empty means the formula is absent, never a substitute seed formula.
     * Join the caller's transaction and protect current-formula selection until commit.
     */
    Optional<FormulaCompositionSnapshot> findById(String formulaVersionId);
}
