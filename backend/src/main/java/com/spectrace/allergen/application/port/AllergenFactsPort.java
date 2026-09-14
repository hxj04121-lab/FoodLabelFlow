package com.spectrace.allergen.application.port;

import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;

import java.util.List;

/** Allergen-owned queries; formula inputs come through catalog ports, never foreign SQL. */
public interface AllergenFactsPort {
    /** Complete canonical catalogue for the jurisdiction, sorted by allergenCode. */
    List<AllergenEntry> listAllergens(String jurisdictionCode);

    /**
     * Derive from all supplied items/components using this exact rule-set version.
     * Preserve unmapped/ambiguous rows as unresolvedComponents. An empty facts list
     * means a completed negative derivation, never unavailable data or a fallback.
     */
    AllergenDerivation derive(
            FormulaCompositionSnapshot formula, String ruleSetVersionId, String jurisdictionCode);
}
