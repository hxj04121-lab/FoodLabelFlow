package com.spectrace.workflow.interfaces.web;

import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelDraftService;
import com.spectrace.label.domain.LabelDraft;
import com.spectrace.shared.api.StrictCommandJson;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.application.ReviewTaskView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class LabelWorkflowController {
    private final LabelReviewService reviews;
    private final LabelDraftService labels;
    private final ExternalActorResolver actors;

    public LabelWorkflowController(LabelReviewService reviews, LabelDraftService labels, ExternalActorResolver actors) {
        this.reviews = reviews;
        this.labels = labels;
        this.actors = actors;
    }

    @PostMapping(value = "/api/labels/{labelVersionId}/review-submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LabelDraft submit(@PathVariable String labelVersionId, @RequestBody String body, HttpServletRequest request) {
        AuthenticatedActor actor = authenticate(request);
        StrictCommandJson.object(body, Set.of(), Set.of());
        reviews.submitForReview(labelVersionId, actor);
        return labels.getById(labelVersionId);
    }

    @PostMapping(value = "/api/labels/{labelVersionId}/review-decisions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LabelDraft decide(@PathVariable String labelVersionId, @RequestBody String body, HttpServletRequest request) {
        AuthenticatedActor actor = authenticate(request);
        var input = StrictCommandJson.object(body, Set.of("decision"), Set.of("comments"));
        String comments = input.has("comments") ? StrictCommandJson.text(input, "comments", 1000) : null;
        reviews.recordDecision(labelVersionId, StrictCommandJson.text(input, "decision", 40), comments, actor);
        return labels.getById(labelVersionId);
    }

    @PostMapping(value = "/api/review-tasks/{reviewTaskId}/publications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LabelDraft publish(@PathVariable String reviewTaskId, @RequestBody String body, HttpServletRequest request) {
        AuthenticatedActor actor = authenticate(request);
        var input = StrictCommandJson.object(body, Set.of("labelVersionId"), Set.of());
        String labelId = StrictCommandJson.text(input, "labelVersionId", 120);
        reviews.publishReviewTask(reviewTaskId, labelId, actor);
        return labels.getById(labelId);
    }

    @GetMapping("/api/review-tasks/{reviewTaskId}")
    public ReviewTaskView getTask(@PathVariable String reviewTaskId, HttpServletRequest request) {
        authenticate(request);
        return reviews.getReviewTask(reviewTaskId);
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
