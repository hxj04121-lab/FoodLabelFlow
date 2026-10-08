package com.spectrace.label.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.port.LabelDraftRepository;
import com.spectrace.label.application.port.ReviewTaskDraftBinding;
import com.spectrace.label.domain.LabelDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class LabelDraftService {
    private final LabelDraftRepository repository;
    private final AuthorizationService authorizationService;
    private final ReviewTaskDraftBinding reviewTaskDraftBinding;

    public LabelDraftService(LabelDraftRepository repository, AuthorizationService authorizationService,
                             ReviewTaskDraftBinding reviewTaskDraftBinding) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.reviewTaskDraftBinding = reviewTaskDraftBinding;
    }

    @Transactional
    public LabelDraft createDraft(String productId, String jurisdictionCode, AuthenticatedActor actor) {
        return createDraft(productId, jurisdictionCode, List.of(), null, actor);
    }

    @Transactional
    public LabelDraft createDraft(String productId, String jurisdictionCode, List<LabelDeclarationInput> declarations,
                                 String reviewTaskId, AuthenticatedActor actor) {
        authorizationService.requirePermission(actor, "LABEL.CREATE");
        required(productId, "productId", 100);
        required(jurisdictionCode, "jurisdictionCode", 40);
        if (reviewTaskId != null) required(reviewTaskId, "reviewTaskId", 140);
        if (declarations == null || declarations.size() > 100 || declarations.stream().anyMatch(row -> row == null)) {
            throw new InvalidLabelDraftRequestException("declarations must be a non-null list with at most 100 entries");
        }
        var distinct = new HashSet<String>();
        for (var row : declarations) {
            // MySQL IDs use a case-insensitive collation; reject aliases before the unique constraint.
            if (!distinct.add(row.allergenId().toLowerCase(java.util.Locale.ROOT))) {
                throw new InvalidLabelDraftRequestException("Duplicate allergen declaration: " + row.allergenId());
            }
        }
        var immutableDeclarations = List.copyOf(declarations);
        // Product lock precedes all task and label reads, serializing first creation and publication.
        reviewTaskDraftBinding.requireFirstDraftAvailable(productId, jurisdictionCode, reviewTaskId);
        LabelDraft draft = repository.createFromCurrentFormula(productId, jurisdictionCode, actor.userId(), immutableDeclarations);
        reviewTaskDraftBinding.bindOpenTaskToDraft(productId, jurisdictionCode, draft.labelVersionId(), reviewTaskId);
        return draft;
    }

    @Transactional(readOnly = true)
    public LabelDraft getById(String labelVersionId) {
        return repository.findById(labelVersionId).orElseThrow(() -> new LabelDraftNotFoundException(labelVersionId));
    }

    private static void required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) {
            throw new InvalidLabelDraftRequestException(field + " must be a non-blank string of at most " + max + " characters");
        }
    }
}
