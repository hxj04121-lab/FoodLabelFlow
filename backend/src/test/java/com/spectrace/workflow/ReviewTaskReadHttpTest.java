package com.spectrace.workflow;

import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import com.spectrace.workflow.application.ReviewTaskReadService;
import com.spectrace.workflow.application.ReviewTaskView;
import com.spectrace.workflow.application.port.ReviewTaskReadRepository;
import com.spectrace.workflow.interfaces.web.ReviewTaskListController;
import com.spectrace.workflow.interfaces.web.ReviewTaskReadErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewTaskReadHttpTest {
    private final IdentityService identities = mock(IdentityService.class);
    private final ReviewTaskReadRepository repository = mock(ReviewTaskReadRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReviewTaskListController(
            new ReviewTaskReadService(repository), new ExternalActorResolver(identities, true)))
            .setControllerAdvice(new IdentityErrors(), new ReviewTaskReadErrors()).build();

    ReviewTaskReadHttpTest() {
        when(identities.authenticate("DEV_EXTERNAL", "reader")).thenReturn(
                new AuthenticatedActor("actual-reader", "reader", "Reader", Set.of(), Set.of()));
    }

    @Test
    void activeActorUsesBoundedDefaultsAndSameBindingDtoAsDetail() throws Exception {
        when(repository.list(20, 0, null)).thenReturn(List.of(new ReviewTaskView(
                "task", "product", "old-label", "draft", "draft", "OPEN", null, null)));
        mvc.perform(get("/api/review-tasks").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "reader").header("X-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reviewTaskId").value("task"))
                .andExpect(jsonPath("$[0].currentLabelVersionId").value("old-label"))
                .andExpect(jsonPath("$[0].draftLabelVersionId").value("draft"))
                .andExpect(jsonPath("$[0].targetLabelVersionId").value("draft"))
                .andExpect(jsonPath("$[0].decision").isEmpty()).andExpect(jsonPath("$[0].resolvedAt").isEmpty());
        verify(repository).list(20, 0, null);
    }

    @Test
    void supportsActualStatusFilterAndPageWithoutChangingActor() throws Exception {
        when(repository.list(10, 20, "IN_REVIEW")).thenReturn(List.of());
        mvc.perform(get("/api/review-tasks?limit=10&offset=20&status=IN_REVIEW")
                        .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "reader"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(repository).list(10, 20, "IN_REVIEW");
        verify(identities).authenticate("DEV_EXTERNAL", "reader");
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=101", "limit=x", "limit=2147483648",
            "offset=-1", "offset=100001", "offset=1.5", "status=IN_PROGRESS", "status=open", "status="})
    void malformedOrUnboundedQueriesCannotReachPersistence(String query) throws Exception {
        mvc.perform(get("/api/review-tasks?" + query).header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "reader"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REVIEW_TASK_QUERY_INVALID"))
                .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.traceId").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void missingIdentityCannotReadEvenWithSpoofedUserOrRoleHeaders() throws Exception {
        mvc.perform(get("/api/review-tasks").header("X-User-Id", "actual-reader").header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(identities, repository);
    }

    @Test
    void unmappedIdentityCannotRead() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "unknown")).thenThrow(new UnknownIdentityException("Not mapped"));
        mvc.perform(get("/api/review-tasks").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "unknown"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(repository);
    }

    @Test
    void persistenceFaultReturnsCanonicalRedactedError() throws Exception {
        when(repository.list(20, 0, null)).thenThrow(new IllegalStateException("SELECT secret_password FROM private_table"));
        mvc.perform(get("/api/review-tasks").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "reader"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The review tasks could not be read"))
                .andExpect(jsonPath("$.evidenceId").isEmpty());
    }
}
