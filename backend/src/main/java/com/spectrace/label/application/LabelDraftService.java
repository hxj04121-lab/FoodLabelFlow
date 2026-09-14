package com.spectrace.label.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.port.LabelDraftRepository;
import com.spectrace.label.domain.LabelDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelDraftService {

    private final LabelDraftRepository repository;
    private final AuthorizationService authorizationService;

    public LabelDraftService(
            LabelDraftRepository repository,
            AuthorizationService authorizationService
    ) {
        this.repository = repository;
        this.authorizationService = authorizationService;
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

        return repository.createFromCurrentFormula(
                productId,
                jurisdictionCode,
                actor.userId()
        );
    }

    @Transactional(readOnly = true)
    public LabelDraft getById(String labelVersionId) {
        return repository.findById(labelVersionId)
                .orElseThrow(() -> new LabelDraftNotFoundException(
                        labelVersionId
                ));
    }
}