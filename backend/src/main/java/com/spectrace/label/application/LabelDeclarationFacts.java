package com.spectrace.label.application;

import com.spectrace.label.application.port.LabelValidationSnapshot;

import java.util.List;

public record LabelDeclarationFacts(
        String labelVersionId,
        String formulaVersionId,
        String ruleSetVersionId,
        String jurisdictionCode,
        List<LabelValidationSnapshot.AllergenDeclaration> declarations
) {
    public LabelDeclarationFacts {
        declarations = List.copyOf(declarations);
    }
}