package com.spectrace.label.application;

import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabelDeclarationQueryServiceTest {

    private final LabelSnapshotPort labels =
            mock(LabelSnapshotPort.class);

    private final LabelDeclarationQueryService service =
            new LabelDeclarationQueryService(labels);

    @Test
    void returnsVersionBoundStructuredDeclarations() {
        var declaration =
                new LabelValidationSnapshot.AllergenDeclaration(
                        "allergen_milk",
                        "CONTAINS",
                        "LABEL",
                        "Contains milk"
                );

        var snapshot =
                new LabelValidationSnapshot(
                        "label_v1",
                        "product_1",
                        "formula_v3",
                        "ruleset_v7",
                        "SG",
                        "milk, sugar",
                        true,
                        "prov_1",
                        List.of(declaration)
                );

        when(labels.findById("label_v1"))
                .thenReturn(Optional.of(snapshot));

        LabelDeclarationFacts result =
                service.getByLabelVersionId("label_v1");

        assertEquals(
                "label_v1",
                result.labelVersionId()
        );

        assertEquals(
                "formula_v3",
                result.formulaVersionId()
        );

        assertEquals(
                "ruleset_v7",
                result.ruleSetVersionId()
        );

        assertEquals(
                1,
                result.declarations().size()
        );

        assertEquals(
                "allergen_milk",
                result.declarations().get(0).allergenId()
        );

        assertEquals(
                "CONTAINS",
                result.declarations().get(0).declarationType()
        );

        assertEquals(
                "LABEL",
                result.declarations().get(0).declarationSource()
        );

        assertEquals(
                "Contains milk",
                result.declarations().get(0).displayText()
        );
    }

    @Test
    void returnsEmptyDeclarationsForExistingLabel() {
        var snapshot =
                new LabelValidationSnapshot(
                        "label_empty",
                        "product_1",
                        "formula_v1",
                        "ruleset_v1",
                        "SG",
                        "",
                        true,
                        "prov_1",
                        List.of()
                );

        when(labels.findById("label_empty"))
                .thenReturn(Optional.of(snapshot));

        LabelDeclarationFacts result =
                service.getByLabelVersionId("label_empty");

        assertEquals(
                0,
                result.declarations().size()
        );
    }

    @Test
    void rejectsMissingLabelVersion() {
        when(labels.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                LabelDraftNotFoundException.class,
                () -> service.getByLabelVersionId("missing")
        );
    }

    @Test
    void declarationFactsDefensivelyCopiesDeclarations() {
        var source = new ArrayList<
                LabelValidationSnapshot.AllergenDeclaration>();

        source.add(
                new LabelValidationSnapshot.AllergenDeclaration(
                        "allergen_soy",
                        "CONTAINS",
                        "LABEL",
                        "Contains soy"
                )
        );

        LabelDeclarationFacts facts =
                new LabelDeclarationFacts(
                        "label_v1",
                        "formula_v1",
                        "ruleset_v1",
                        source
                );

        source.clear();

        assertEquals(
                1,
                facts.declarations().size()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> facts.declarations().clear()
        );
    }
}