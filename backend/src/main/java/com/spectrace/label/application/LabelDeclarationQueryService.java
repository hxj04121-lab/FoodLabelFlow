package com.spectrace.label.application;

import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import org.springframework.stereotype.Service;

@Service
public class LabelDeclarationQueryService {

    private final LabelSnapshotPort labels;

    public LabelDeclarationQueryService(
            LabelSnapshotPort labels
    ) {
        this.labels = labels;
    }

    public LabelDeclarationFacts getByLabelVersionId(
            String labelVersionId
    ) {
        LabelValidationSnapshot label =
                labels.findById(labelVersionId)
                        .orElseThrow(() ->
                                new LabelDraftNotFoundException(
                                        labelVersionId
                                )
                        );

        return new LabelDeclarationFacts(
                label.labelVersionId(),
                label.formulaVersionId(),
                label.ruleSetVersionId(),
                label.declarations()
        );
    }
}