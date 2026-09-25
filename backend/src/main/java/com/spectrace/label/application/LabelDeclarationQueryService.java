package com.spectrace.label.application;

import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelDeclarationQueryService {

    private final LabelSnapshotPort labels;

    public LabelDeclarationQueryService(
            LabelSnapshotPort labels
    ) {
        this.labels = labels;
    }

    // Snapshot ports use locking reads; keep one transaction, not JDBC read-only mode.
    @Transactional
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
                label.jurisdictionCode(),
                label.declarations()
        );
    }
}
