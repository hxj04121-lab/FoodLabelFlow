package com.spectrace.identity;

import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.CurrentIdentityController;
import com.spectrace.identity.interfaces.web.CurrentIdentityReadErrors;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CurrentIdentityHttpTest {
    private final IdentityService identities = mock(IdentityService.class);

    private MockMvc mvc(boolean devEnabled) {
        return MockMvcBuilders.standaloneSetup(new CurrentIdentityController(new ExternalActorResolver(identities, devEnabled)))
                .setControllerAdvice(new IdentityErrors(), new CurrentIdentityReadErrors()).build();
    }

    @Test
    void returnsOnlyCurrentMappedIdentityAndActualSortedPermissions() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "maker")).thenReturn(new AuthenticatedActor(
                "mapped-maker", "maker", "Actual Maker", Set.of("LABEL_OFFICER"),
                Set.of("LABEL.VALIDATE", "LABEL.CREATE")));
        mvc(true).perform(get("/api/identity/current").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "maker").header("X-Role", "APPROVER")
                        .header("X-Permissions", "LABEL.APPROVE").param("userId", "approver"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("mapped-maker"))
                .andExpect(jsonPath("$.username").value("maker")).andExpect(jsonPath("$.displayName").value("Actual Maker"))
                .andExpect(jsonPath("$.roles[0]").value("LABEL_OFFICER")).andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("LABEL.CREATE"))
                .andExpect(jsonPath("$.permissions[1]").value("LABEL.VALIDATE"))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.externalSubject").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(identities).authenticate("DEV_EXTERNAL", "maker");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV_EXTERNAL", "dev_external", " Dev_External "})
    void disabledDevelopmentIdentityIsRejectedBeforeAnyRepositoryLookup(String provider) throws Exception {
        mvc(false).perform(get("/api/identity/current").header("X-Auth-Provider", provider)
                        .header("X-External-Subject", "maker"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(identities);
    }

    @Test
    void disabledDevelopmentFlagDoesNotInventOrBlockAnotherMappedProvider() throws Exception {
        when(identities.authenticate("trusted-provider", "subject")).thenReturn(new AuthenticatedActor(
                "mapped", "reader", "Reader", Set.of(), Set.of()));
        mvc(false).perform(get("/api/identity/current").header("X-Auth-Provider", "trusted-provider")
                        .header("X-External-Subject", "subject"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("mapped"))
                .andExpect(jsonPath("$.permissions").isArray()).andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void missingIdentityHasCanonical401AndNoLookup() throws Exception {
        mvc(true).perform(get("/api/identity/current").header("X-User-Id", "admin"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isEmpty()).andExpect(jsonPath("$.evidenceId").isEmpty());
        verifyNoInteractions(identities);
    }

    @Test
    void inactiveOrUnmappedSubjectRemains401() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "inactive")).thenThrow(new UnknownIdentityException("Not active"));
        mvc(true).perform(get("/api/identity/current").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "inactive"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authorizationDenialKeepsCanonical403() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "denied")).thenThrow(new AuthorizationDeniedException("Denied"));
        mvc(true).perform(get("/api/identity/current").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "denied"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void identityPersistenceFaultDoesNotExposeStorageDetails() throws Exception {
        when(identities.authenticate("DEV_EXTERNAL", "reader")).thenThrow(new IllegalStateException("password_hash SQL private_table"));
        mvc(true).perform(get("/api/identity/current").header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", "reader"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The current identity could not be read"));
    }
}
