package com.spectrace.label.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.port.LabelDraftRepository;
import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import com.spectrace.label.domain.LabelDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelDraftService {

    private final LabelDraftRepository repository;
    private final AuthorizationService authorizationService;
    private final ReviewTaskDraftBinding reviewTaskDraftBinding;

    public LabelDraftService(
            LabelDraftRepository repository,
            AuthorizationService authorizationService,
            ReviewTaskDraftBinding reviewTaskDraftBinding
    ) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.reviewTaskDraftBinding = reviewTaskDraftBinding;
    }

    @Transactional
    public LabelDraft createDraft(
            String productId,
            String jurisdictionCode,
            AuthenticatedActor actor
    ) {
        authorizationService.requirePermission(
                actor,
                "LABEL.CREATE"
        );

        LabelDraft draft = repository.createFromCurrentFormula(
                productId,
                jurisdictionCode,
                actor.userId()
        );
        reviewTaskDraftBinding.bindOpenTaskToDraft(
                productId,
                jurisdictionCode,
                draft.labelVersionId()
        );
        return draft;
    }

    @Transactional(readOnly = true)
    public LabelDraft getById(String labelVersionId) {
        return repository.findById(labelVersionId)
                .orElseThrow(() -> new LabelDraftNotFoundException(
                        labelVersionId
                ));
    }
}
