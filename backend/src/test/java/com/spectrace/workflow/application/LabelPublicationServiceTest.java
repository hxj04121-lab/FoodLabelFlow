package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.port.LabelPublicationRepository;
import com.spectrace.workflow.application.port.LabelPublicationRepository.PublicationTarget;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LabelPublicationServiceTest {

    private final AuthorizationService authorizationService =
            new AuthorizationService();

    private final LabelPublicationRepository repository =
            mock(LabelPublicationRepository.class);

    private final LabelTransitionPolicy transitionPolicy =
            new LabelTransitionPolicy();

    private final LabelPublicationService service =
            new LabelPublicationService(
                    authorizationService,
                    repository,
                    transitionPolicy
            );

    @Test
    void publishesApprovedLabelWithApproveRecord() {
        when(repository.lockForPublication("label_v2"))
                .thenReturn(Optional.of(target("APPROVED")));

        when(repository.hasApproveRecord("label_v2"))
                .thenReturn(true);

        when(repository.markPublished("label_v2"))
                .thenReturn(1);

        service.publishLabel(
                "label_v2",
                publisher()
        );

        verify(repository).supersedeCurrentPublished(
                "product_1",
                "SG"
        );

        verify(repository).markPublished("label_v2");

        verify(repository).updateCurrentPublishedPointer(
                "product_1",
                "label_v2"
        );

        verify(repository).linkAndResolveReviewTask("label_v2");

        verify(repository).createPublicationRecord(
                "label_v2",
                "user_publisher",
                "prov_1"
        );

        verify(repository).createPublicationAudit(
                "label_v2",
                "user_publisher",
                "prov_1"
        );
    }

    @Test
    void rejectsPublicationWithoutPermission() {
        assertThrows(
                AuthorizationDeniedException.class,
                () -> service.publishLabel(
                        "label_v2",
                        actorWithoutPublishPermission()
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsPublicationFromInvalidState() {
        when(repository.lockForPublication("label_v2"))
                .thenReturn(Optional.of(target("DRAFT")));

        assertThrows(
                IllegalStateException.class,
                () -> service.publishLabel(
                        "label_v2",
                        publisher()
                )
        );

        verify(repository, never())
                .markPublished(anyString());
    }

    @Test
    void rejectsPublicationWithoutApproveRecord() {
        when(repository.lockForPublication("label_v2"))
                .thenReturn(Optional.of(target("APPROVED")));

        when(repository.hasApproveRecord("label_v2"))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> service.publishLabel(
                        "label_v2",
                        publisher()
                )
        );

        verify(repository, never())
                .supersedeCurrentPublished(
                        anyString(),
                        anyString()
                );

        verify(repository, never())
                .markPublished(anyString());
    }

    @Test
    void rejectsPublicationWhenFormulaIsNoLongerCurrent() {
        PublicationTarget stale =
                new PublicationTarget(
                        "label_v2",
                        "product_1",
                        "formula_old",
                        "formula_current",
                        "SG",
                        "APPROVED",
                        "prov_1"
                );

        when(repository.lockForPublication("label_v2"))
                .thenReturn(Optional.of(stale));

        assertThrows(
                RuntimeException.class,
                () -> service.publishLabel(
                        "label_v2",
                        publisher()
                )
        );

        verify(repository, never())
                .markPublished(anyString());
    }

    private PublicationTarget target(
            String status
    ) {
        return new PublicationTarget(
                "label_v2",
                "product_1",
                "formula_v1",
                "formula_v1",
                "SG",
                status,
                "prov_1"
        );
    }

    private AuthenticatedActor publisher() {
        return new AuthenticatedActor(
                "user_publisher",
                "publisher",
                "Publisher",
                Set.of(),
                Set.of("LABEL.PUBLISH")
        );
    }

    private AuthenticatedActor actorWithoutPublishPermission() {
        return new AuthenticatedActor(
                "user_reader",
                "reader",
                "Reader",
                Set.of(),
                Set.of()
        );
    }
}