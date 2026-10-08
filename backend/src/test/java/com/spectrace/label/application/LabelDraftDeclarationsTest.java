package com.spectrace.label.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.port.LabelDraftRepository;
import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LabelDraftDeclarationsTest {
    private final LabelDraftRepository repository = mock(LabelDraftRepository.class);
    private final ReviewTaskDraftBinding bindings = mock(ReviewTaskDraftBinding.class);
    private final LabelDraftService service = new LabelDraftService(repository, new AuthorizationService(), bindings);
    private final AuthenticatedActor actor = new AuthenticatedActor("maker", "maker", "Maker", Set.of(), Set.of("LABEL.CREATE"));

    @Test
    void caseInsensitiveDuplicateIdsAreRejectedBeforeLockOrWrite() {
        assertThrows(InvalidLabelDraftRequestException.class, () -> service.createDraft("product", "US",
                List.of(new LabelDeclarationInput("all_soy", "CONTAINS", null),
                        new LabelDeclarationInput("ALL_SOY", "CONTAINS", null)), "task", actor));
        verifyNoInteractions(repository, bindings);
    }

    @Test
    void declarationShapeCannotClaimUnsupportedTypeOrOversizeDisplay() {
        assertThrows(InvalidLabelDraftRequestException.class,
                () -> new LabelDeclarationInput("all_soy", "MAY_CONTAIN", null));
        assertThrows(InvalidLabelDraftRequestException.class,
                () -> new LabelDeclarationInput("all_soy", "CONTAINS", "x".repeat(301)));
        assertThrows(InvalidLabelDraftRequestException.class,
                () -> new LabelDeclarationInput(" ", "CONTAINS", null));
    }

    @Test
    void anAlreadyBoundTaskStopsBeforeAnyLabelOrDeclarationInsertion() {
        doThrow(new LabelVersionConflictException("already bound")).when(bindings)
                .requireFirstDraftAvailable("product", "US", "task");
        assertThrows(LabelVersionConflictException.class, () -> service.createDraft("product", "US",
                List.of(new LabelDeclarationInput("all_soy", "CONTAINS", null)), "task", actor));
        verifyNoInteractions(repository);
    }
}
