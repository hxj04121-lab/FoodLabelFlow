package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelDraftNotFoundException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.workflow.application.port.LabelPublicationRepository;
import com.spectrace.workflow.application.port.LabelPublicationRepository.PublicationTarget;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelPublicationService {

    private final AuthorizationService authorizationService;
    private final LabelPublicationRepository publicationRepository;
    private final LabelTransitionPolicy transitionPolicy;

    public LabelPublicationService(
            AuthorizationService authorizationService,
            LabelPublicationRepository publicationRepository,
            LabelTransitionPolicy transitionPolicy
    ) {
        this.authorizationService = authorizationService;
        this.publicationRepository = publicationRepository;
        this.transitionPolicy = transitionPolicy;
    }

    @Transactional
    public void publishLabel(
            String labelVersionId,
            AuthenticatedActor actor
    ) {
        authorizationService.requirePermission(
                actor,
                "LABEL.PUBLISH"
        );

        PublicationTarget target =
                publicationRepository
                        .lockForPublication(labelVersionId)
                        .orElseThrow(() ->
                                new LabelDraftNotFoundException(
                                        labelVersionId
                                )
                        );

        transitionPolicy.requirePublishable(
                target.lifecycleStatus()
        );

        if (!target.formulaVersionId().equals(
                target.currentFormulaVersionId()
        )) {
            throw new LabelVersionConflictException(
                    "Label formula is no longer the "
                            + "product current formula: "
                            + labelVersionId
            );
        }

        if (!publicationRepository.hasApproveRecord(
                labelVersionId
        )) {
            throw new IllegalStateException(
                    "Publication requires an APPROVE record"
            );
        }

        publicationRepository.supersedeCurrentPublished(
                target.productId(),
                target.jurisdictionCode()
        );

        int published =
                publicationRepository.markPublished(
                        labelVersionId
                );

        if (published != 1) {
            throw new IllegalStateException(
                    "Publication state transition failed"
            );
        }

        publicationRepository.updateCurrentPublishedPointer(
                target.productId(),
                labelVersionId
        );

        publicationRepository.linkAndResolveReviewTask(
                labelVersionId
        );

        publicationRepository.createPublicationRecord(
                labelVersionId,
                actor.userId(),
                target.dataProvenanceId()
        );

        publicationRepository.createPublicationAudit(
                labelVersionId,
                actor.userId(),
                target.dataProvenanceId()
        );
    }
}