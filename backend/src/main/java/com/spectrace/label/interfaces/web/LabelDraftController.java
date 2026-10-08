package com.spectrace.label.interfaces.web;

import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.ExternalActorResolver;
import org.springframework.beans.factory.annotation.Autowired;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.InvalidLabelDraftRequestException;
import com.spectrace.label.application.LabelDeclarationFacts;
import com.spectrace.label.application.LabelDeclarationInput;
import com.spectrace.label.application.LabelDeclarationQueryService;
import com.spectrace.label.application.LabelDraftService;
import com.spectrace.label.domain.LabelDraft;
import com.spectrace.shared.api.StrictCommandJson;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/labels")
public class LabelDraftController {
    private final LabelDraftService service;
    private final ExternalActorResolver actors;
    private final LabelDeclarationQueryService declarations;

    public LabelDraftController(LabelDraftService service, IdentityService identityService,
                                LabelDeclarationQueryService declarations) {
        this(service, identityService, declarations, new ExternalActorResolver(identityService, false));
    }

    @Autowired
    public LabelDraftController(LabelDraftService service, IdentityService identityService,
                                LabelDeclarationQueryService declarations, ExternalActorResolver actors) {
        this.service = service;
        this.actors = actors;
        this.declarations = declarations;
    }

    @PostMapping(value = "/drafts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LabelDraft> createDraft(@RequestBody String body, HttpServletRequest request) {
        AuthenticatedActor actor = authenticate(request);
        var input = StrictCommandJson.object(body, Set.of("productId", "jurisdictionCode"),
                Set.of("declarations", "reviewTaskId"));
        String productId = StrictCommandJson.text(input, "productId", 100);
        String jurisdiction = StrictCommandJson.text(input, "jurisdictionCode", 40);
        String taskId = input.has("reviewTaskId") ? StrictCommandJson.text(input, "reviewTaskId", 140) : null;
        List<LabelDeclarationInput> entered = new ArrayList<>();
        if (input.has("declarations")) {
            var rows = input.get("declarations");
            if (!rows.isArray() || rows.size() > 100) {
                throw new InvalidLabelDraftRequestException("declarations must be an array with at most 100 entries");
            }
            for (var row : rows) {
                StrictCommandJson.checkFields(row, Set.of("allergenId", "declarationType"), Set.of("displayText"));
                entered.add(new LabelDeclarationInput(
                        StrictCommandJson.text(row, "allergenId", 80),
                        StrictCommandJson.text(row, "declarationType", 40),
                        row.has("displayText") ? StrictCommandJson.text(row, "displayText", 300) : null));
            }
        }
        // Keep the original application call for legacy clients that omit the new fields.
        LabelDraft draft = !input.has("declarations") && taskId == null
                ? service.createDraft(productId, jurisdiction, actor)
                : service.createDraft(productId, jurisdiction, entered, taskId, actor);
        return ResponseEntity.created(URI.create("/api/labels/" + draft.labelVersionId())).body(draft);
    }

    @GetMapping("/{labelVersionId}")
    public ResponseEntity<LabelDraft> getById(@PathVariable String labelVersionId, HttpServletRequest request) {
        authenticate(request);
        return ResponseEntity.ok(service.getById(labelVersionId));
    }

    @GetMapping("/{labelVersionId}/declarations")
    public ResponseEntity<LabelDeclarationFacts> getDeclarations(@PathVariable String labelVersionId, HttpServletRequest request) {
        authenticate(request);
        return ResponseEntity.ok(declarations.getByLabelVersionId(labelVersionId));
    }

    private AuthenticatedActor authenticate(HttpServletRequest request) {
        String provider = request.getHeader("X-Auth-Provider");
        String subject = request.getHeader("X-External-Subject");
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new UnknownIdentityException("Authenticated identity headers are required");
        }
        return actors.resolve(provider, subject);
    }
}
