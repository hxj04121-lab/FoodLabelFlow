package com.spectrace.audit;

import com.spectrace.audit.application.ProductLabelHistory;
import com.spectrace.audit.application.ProductLabelHistoryReadService;
import com.spectrace.audit.interfaces.web.ProductLabelHistoryController;
import com.spectrace.audit.interfaces.web.ProductLabelHistoryErrors;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import com.spectrace.identity.domain.AuthenticatedActor;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductLabelHistoryControllerTest {
    private static final String PATH = "/api/audit/products/product_1/label-history";
    private final IdentityService identities = mock(IdentityService.class);
    private final ProductLabelHistoryReadService history = mock(ProductLabelHistoryReadService.class);
    private final ExternalActorResolver actors = new ExternalActorResolver(identities, true);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductLabelHistoryController(
                    history, actors, new AuthorizationService()))
            .setControllerAdvice(new IdentityErrors(), new ProductLabelHistoryErrors()).build();

    @Test
    void returnsTheProductScopedHistoryToAnAuditor() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "auditor"))
                .thenReturn(new AuthenticatedActor("auditor_1", "audit.viewer", "Audit Viewer",
                        Set.of("AUDITOR"), Set.of("AUDIT.READ")));
        when(history.getProductLabelHistory("product_1")).thenReturn(new ProductLabelHistory(
                "product_1", "Sample product", List.of(), List.of(), List.of()));

        mvc.perform(get(PATH).header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("product_1"))
                .andExpect(jsonPath("$.productDisplayName").value("Sample product"))
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.approvals").isArray())
                .andExpect(jsonPath("$.auditEvents").isArray());

        verify(history).getProductLabelHistory("product_1");
    }

    @Test
    void deniesAConnectedIdentityWithoutAuditReadBeforeQueryingHistory() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "maker"))
                .thenReturn(new AuthenticatedActor("maker_1", "maker", "Maker",
                        Set.of("LABEL_OFFICER"), Set.of("LABEL.CREATE")));

        mvc.perform(get(PATH).header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "maker"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));

        verifyNoInteractions(history);
    }

    @Test
    void requiresAnAuthenticatedIdentity() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(identities, history);
    }
}
