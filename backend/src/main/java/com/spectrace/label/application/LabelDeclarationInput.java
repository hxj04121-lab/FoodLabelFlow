package com.spectrace.label.application;

/** Explicit, immutable first-draft input. The source is always assigned by the server. */
public record LabelDeclarationInput(String allergenId, String declarationType, String displayText) {
    public LabelDeclarationInput {
        if (allergenId == null || allergenId.isBlank() || allergenId.length() > 80
                || !allergenId.equals(allergenId.trim())) {
            throw new InvalidLabelDraftRequestException("allergenId must be a non-blank catalog ID of at most 80 characters");
        }
        if (!"CONTAINS".equals(declarationType)) {
            throw new InvalidLabelDraftRequestException("declarationType must be CONTAINS");
        }
        if (displayText != null && (displayText.isBlank() || displayText.length() > 300)) {
            throw new InvalidLabelDraftRequestException("displayText must be non-blank and at most 300 characters when provided");
        }
    }
}
