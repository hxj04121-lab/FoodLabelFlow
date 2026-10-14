package com.spectrace.audit.interfaces.web;

import com.spectrace.audit.application.ProductLabelHistoryService;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductLabelHistoryHttpTest {
    private static final String PATH = "/api/audit/products/product-1/label-history";
    private final IdentityService identities = mock(IdentityService.class);
    private final ProductLabelHistoryService history = mock(ProductLabelHistoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductLabelHistoryController(history,
                    new ExternalActorResolver(identities, true), new AuthorizationService()))
            .setControllerAdvice(new IdentityErrors(), new ProductLabelHistoryErrors()).build();

    @Test
    void requiresAuditReadBeforeQueryingProductHistory() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "maker")).thenReturn(
                new AuthenticatedActor("maker-1", "maker", "Maker", Set.of("LABEL_OFFICER"), Set.of("LABEL.CREATE")));

        mvc.perform(get(PATH).header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "maker"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));

        verify(identities).authenticate("DEV_EXTERNAL", "maker");
        verifyNoInteractions(history);
    }

    @Test
    void auditReaderGetsOnlyTheRequestedProductHistory() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "change-manager")).thenReturn(
                new AuthenticatedActor("change-manager-1", "change.manager", "Change Manager",
                        Set.of("CHANGE_MANAGER"), Set.of("AUDIT.READ")));
        when(history.get("product-1")).thenReturn(new ProductLabelHistoryService.ProductLabelHistory(
                "product-1", "Chocolate product", java.util.List.of(), java.util.List.of(), java.util.List.of()));

        mvc.perform(get(PATH).header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "change-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("product-1"))
                .andExpect(jsonPath("$.productDisplayName").value("Chocolate product"))
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.approvals").isArray())
                .andExpect(jsonPath("$.auditEvents").isArray());

        verify(history).get("product-1");
        verifyNoMoreInteractions(history);
    }

    @Test
    void missingExternalIdentityCannotReadAuditHistory() throws Exception {
        mvc.perform(get(PATH).header("X-User-Id", "change-manager-1").header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(identities, history);
    }
}
