package com.spectrace.label;

import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import com.spectrace.label.application.LabelDeclarationInput;
import com.spectrace.label.application.LabelDeclarationQueryService;
import com.spectrace.label.application.LabelDraftService;
import com.spectrace.label.domain.LabelDraft;
import com.spectrace.label.interfaces.web.LabelCommandErrors;
import com.spectrace.label.interfaces.web.LabelDraftController;
import com.spectrace.label.interfaces.web.LabelErrors;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.interfaces.web.LabelWorkflowController;
import com.spectrace.workflow.interfaces.web.WorkflowErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LabelProductCommandHttpTest {
    private final LabelDraftService labels = mock(LabelDraftService.class);
    private final LabelReviewService reviews = mock(LabelReviewService.class);
    private final IdentityService identities = mock(IdentityService.class);
    private final LabelDeclarationQueryService declarations = mock(LabelDeclarationQueryService.class);
    private final AuthenticatedActor actor = new AuthenticatedActor("maker", "maker", "Maker", Set.of(), Set.of("LABEL.CREATE"));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new LabelDraftController(labels, identities, declarations),
            new LabelWorkflowController(reviews, labels, new ExternalActorResolver(identities, false)))
            .setControllerAdvice(new IdentityErrors(), new LabelErrors(), new LabelCommandErrors(), new WorkflowErrors())
            .build();

    LabelProductCommandHttpTest() {
        when(identities.authenticate("demo", "maker")).thenReturn(actor);
    }

    @Test
    void explicitDeclarationsUseTheAuthenticatedActorAndActualTaskId() throws Exception {
        var entered = List.of(new LabelDeclarationInput("all_soy", "CONTAINS", "Contains soy"));
        when(labels.createDraft("product", "US", entered, "review", actor)).thenReturn(draft());
        mvc.perform(post("/api/labels/drafts").header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId":"product","jurisdictionCode":"US","reviewTaskId":"review",
                         "declarations":[{"allergenId":"all_soy","declarationType":"CONTAINS","displayText":"Contains soy"}]}
                        """)).andExpect(status().isCreated()).andExpect(jsonPath("$.labelVersionId").value("label"));
        verify(labels).createDraft("product", "US", entered, "review", actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"productId\":\"product\",\"jurisdictionCode\":\"US\",\"actorUserId\":\"admin\"}",
            "{\"productId\":\"product\",\"productId\":\"other\",\"jurisdictionCode\":\"US\"}",
            "{\"productId\":\"product\",\"jurisdictionCode\":\"US\"} {}",
            "{\"productId\":\"product\",\"jurisdictionCode\":\"US\",\"declarations\":null}",
            "{\"productId\":\"product\",\"jurisdictionCode\":\"US\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"CONTAINS\",\"declarationSource\":\"FORMULA_DERIVED\"}]}",
            "{\"productId\":\"product\",\"jurisdictionCode\":\"US\",\"declarations\":[{\"allergenId\":\"all_soy\",\"declarationType\":\"MAY_CONTAIN\"}]}"
    })
    void rejectsAmbiguousOrSpoofedDraftInputBeforeAnyWrite(String body) throws Exception {
        mvc.perform(post("/api/labels/drafts").header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(labels, reviews);
    }

    @Test
    void decisionCommentBoundaryMatchesTheDatabaseContract() throws Exception {
        when(labels.getById("label")).thenReturn(draft());
        String accepted = "a".repeat(1000);
        mvc.perform(post("/api/labels/label/review-decisions")
                .header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"comments\":\"" + accepted + "\"}"))
                .andExpect(status().isOk());
        verify(reviews).recordDecision("label", "APPROVE", accepted, actor);
        clearInvocations(reviews, labels);
        mvc.perform(post("/api/labels/label/review-decisions")
                .header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"comments\":\"" + accepted + "a\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LABEL_COMMAND_INVALID"));
        verifyNoInteractions(reviews, labels);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/labels/drafts", "/api/labels/label/review-submissions",
            "/api/labels/label/review-decisions", "/api/review-tasks/review/publications"})
    void missingBodyReturnsCanonicalErrorAndNeverWrites(String path) throws Exception {
        mvc.perform(post(path).header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LABEL_COMMAND_INVALID"));
        verifyNoInteractions(labels, reviews);
    }

    @Test
    void workflowRejectsAnActorInTheRequestInsteadOfTrustingIt() throws Exception {
        mvc.perform(post("/api/labels/label/review-decisions")
                .header("X-Auth-Provider", "demo").header("X-External-Subject", "maker")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"actorUserId\":\"checker\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LABEL_COMMAND_INVALID"));
        verifyNoInteractions(reviews, labels);
    }

    private static LabelDraft draft() {
        return new LabelDraft("label", "product", "formula", "rules", "US", 2, "soy", "DRAFT",
                "N", "maker", LocalDateTime.of(2026, 10, 8, 0, 0), "provenance");
    }
}
