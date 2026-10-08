package com.spectrace.label.application.port;

import com.spectrace.label.application.LabelDeclarationInput;
import com.spectrace.label.domain.LabelDraft;
import java.util.List;
import java.util.Optional;

public interface LabelDraftRepository {
    default LabelDraft createFromCurrentFormula(String productId, String jurisdictionCode, String actorUserId) {
        return createFromCurrentFormula(productId, jurisdictionCode, actorUserId, List.of());
    }

    LabelDraft createFromCurrentFormula(String productId, String jurisdictionCode, String actorUserId,
                                       List<LabelDeclarationInput> declarations);

    Optional<LabelDraft> findById(String labelVersionId);
}
