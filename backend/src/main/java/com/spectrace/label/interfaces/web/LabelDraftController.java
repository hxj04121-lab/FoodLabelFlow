package com.spectrace.label.interfaces.web;

import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelDraftService;
import com.spectrace.label.domain.LabelDraft;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/labels")
public class LabelDraftController {

    private static final String AUTH_PROVIDER_HEADER =
            "X-Auth-Provider";

    private static final String AUTH_SUBJECT_HEADER =
            "X-External-Subject";

    private final LabelDraftService service;
    private final IdentityService identityService;

    public LabelDraftController(
            LabelDraftService service,
            IdentityService identityService
    ) {
        this.service = service;
        this.identityService = identityService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<LabelDraft> createDraft(
            @RequestBody CreateDraftRequest body,
            HttpServletRequest request
    ) {
        AuthenticatedActor actor = authenticate(request);

        LabelDraft draft = service.createDraft(
                body.productId(),
                body.jurisdictionCode(),
                actor
        );

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/labels/"
                                        + draft.labelVersionId()
                        )
                )
                .body(draft);
    }

    @GetMapping("/{labelVersionId}")
    public ResponseEntity<LabelDraft> getById(
            @PathVariable String labelVersionId,
            HttpServletRequest request
    ) {
        authenticate(request);

        return ResponseEntity.ok(
                service.getById(labelVersionId)
        );
    }

    private AuthenticatedActor authenticate(
            HttpServletRequest request
    ) {
        String provider = request.getHeader(
                AUTH_PROVIDER_HEADER
        );

        String subject = request.getHeader(
                AUTH_SUBJECT_HEADER
        );

        if (provider == null
                || provider.isBlank()
                || subject == null
                || subject.isBlank()) {

            throw new UnknownIdentityException(
                    "Authenticated identity headers are required"
            );
        }

        return identityService.authenticate(
                provider,
                subject
        );
    }

    public record CreateDraftRequest(
            String productId,
            String jurisdictionCode
    ) {
    }
}