package com.spectrace.label.application.port;

import com.spectrace.label.domain.LabelDraft;

import java.util.Optional;

public interface LabelDraftRepository {

    LabelDraft createFromCurrentFormula(
            String productId,
            String jurisdictionCode,
            String actorUserId
    );

    Optional<LabelDraft> findById(
            String labelVersionId
    );
}