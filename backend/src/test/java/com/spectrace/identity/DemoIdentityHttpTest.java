package com.spectrace.identity;

import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.port.IdentityRepository;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.CurrentIdentityController;
import com.spectrace.identity.interfaces.web.CurrentIdentityReadErrors;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP serialization, resolver and identity service; only the persistence port is mocked. */
class DemoIdentityHttpTest {
    private static final List<String> DEMO_SUBJECTS = List.of(
            "dev-external-label-officer", "dev-external-qa-approver", "dev-external-publisher");
    private final IdentityRepository repository = mock(IdentityRepository.class);

    private MockMvc mvc(boolean demoEnabled, String runtime, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        ExternalActorResolver resolver = new ExternalActorResolver(
                new IdentityService(repository), demoEnabled, environment, runtime);
        return MockMvcBuilders.standaloneSetup(new CurrentIdentityController(resolver))
                .setControllerAdvice(new IdentityErrors(), new CurrentIdentityReadErrors()).build();
    }

    private static AuthenticatedActor actor(int index) {
        return switch (index) {
            case 0 -> new AuthenticatedActor("mapped-maker", "maker", "Mapped Maker",
                    Set.of("LABEL_OFFICER"), Set.of("LABEL.VALIDATE", "LABEL.CREATE"));
            case 1 -> new AuthenticatedActor("mapped-checker", "checker", "Mapped Checker",
                    Set.of("QA_APPROVER"), Set.of("LABEL.REVIEW", "LABEL.APPROVE"));
            case 2 -> new AuthenticatedActor("mapped-publisher", "publisher", "Mapped Publisher",
                    Set.of("LABEL_PUBLISHER"), Set.of("LABEL.REVIEW", "LABEL.PUBLISH"));
            default -> throw new IllegalArgumentException("Unknown demo actor");
        };
    }

    private void mapDemoActors() {
        for (int index = 0; index < DEMO_SUBJECTS.size(); index++) {
            when(repository.findActiveActorByExternalSubject("DEV_EXTERNAL", DEMO_SUBJECTS.get(index)))
                    .thenReturn(Optional.of(actor(index)));
        }
    }

    @Test
    void enabledDemoOptionsExposeExactlyTheMappedMakerCheckerAndPublisher() throws Exception {
        mapDemoActors();
        ResultActions result = mvc(true, "development").perform(get("/api/identity/demo-options")
                .header("X-User-Id", "admin").header("X-Role", "ADMIN")
                .header("X-Permissions", "LABEL.APPROVE,LABEL.PUBLISH").param("userId", "admin"));
        result.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        List<String> keys = List.of("MAKER", "CHECKER", "PUBLISHER");
        for (int index = 0; index < keys.size(); index++) {
            AuthenticatedActor expected = actor(index);
            String option = "$[" + index + "]";
            result.andExpect(jsonPath(option + ".key").value(keys.get(index)))
                    .andExpect(jsonPath(option + ".subject").value(DEMO_SUBJECTS.get(index)))
                    .andExpect(jsonPath(option + ".actor.userId").value(expected.userId()))
                    .andExpect(jsonPath(option + ".actor.username").value(expected.username()))
                    .andExpect(jsonPath(option + ".actor.displayName").value(expected.displayName()))

                    .andExpect(jsonPath(option + ".actor.externalSubject").doesNotExist())
                    .andExpect(jsonPath(option + ".actor.email").doesNotExist())
                    .andExpect(jsonPath(option + ".actor.passwordHash").doesNotExist());
            expectMappedRolesAndPermissions(result, option + ".actor", expected);
            verify(repository).findActiveActorByExternalSubject("DEV_EXTERNAL", DEMO_SUBJECTS.get(index));
        }
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void selectingADemoSubjectUsesItsMappedIdentityAndIgnoresClientPrivileges(int index) throws Exception {
        mapDemoActors();
        AuthenticatedActor expected = actor(index);
        ResultActions result = mvc(true, "development").perform(get("/api/identity/current")
                        .header("X-Auth-Provider", "DEV_EXTERNAL")
                        .header("X-External-Subject", DEMO_SUBJECTS.get(index))
                        .header("X-User-Id", "admin").header("X-Role", "ADMIN")
                        .header("X-Permissions", "ALL").param("userId", "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(expected.userId()));
        expectMappedRolesAndPermissions(result, "$", expected);
        verify(repository).findActiveActorByExternalSubject("DEV_EXTERNAL", DEMO_SUBJECTS.get(index));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("disabledDemoConfigurations")
    void disabledOrProductionConfigurationRejectsDemoOptionsAndSpoofedActorsBeforeLookup(
            boolean demoEnabled, String runtime, String[] profiles) throws Exception {
        MockMvc http = mvc(demoEnabled, runtime, profiles);
        expectAuthenticationRequired(http.perform(get("/api/identity/demo-options")
                .header("X-User-Id", "admin").param("userId", "admin")));
        for (String provider : List.of("DEV_EXTERNAL", "dev_external", " Dev_External ")) {
            expectAuthenticationRequired(http.perform(get("/api/identity/current")
                    .header("X-Auth-Provider", provider)
                    .header("X-External-Subject", "dev-external-admin")
                    .header("X-User-Id", "admin").header("X-Role", "ADMIN")
                    .header("X-Permissions", "ALL").param("userId", "admin")));
        }
        verifyNoInteractions(repository);
    }

    static Stream<Arguments> disabledDemoConfigurations() {
        return Stream.of(
                Arguments.of(false, "development", new String[0]),
                Arguments.of(true, "prod", new String[0]),
                Arguments.of(true, "production", new String[0]),
                Arguments.of(true, "PrOd", new String[0]),
                Arguments.of(true, "PRODUCTION", new String[0]),
                Arguments.of(true, "development", new String[]{"prod"}),
                Arguments.of(true, "test", new String[]{"production"}));
    }

    @Test
    void productionDemoGuardStillAllowsAnActuallyMappedTrustedProvider() throws Exception {
        AuthenticatedActor trusted = new AuthenticatedActor("mapped-sso-reader", "sso-reader", "SSO Reader",
                Set.of("READER"), Set.of("CATALOG.READ"));
        when(repository.findActiveActorByExternalSubject("trusted-provider", "mapped-subject"))
                .thenReturn(Optional.of(trusted));
        MockMvc http = mvc(true, "production");
        expectAuthenticationRequired(http.perform(get("/api/identity/current")
                .header("X-Auth-Provider", "DEV_EXTERNAL").header("X-External-Subject", "dev-external-admin")));
        http.perform(get("/api/identity/current").header("X-Auth-Provider", "trusted-provider")
                        .header("X-External-Subject", "mapped-subject").header("X-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("mapped-sso-reader"))
                .andExpect(jsonPath("$.roles[0]").value("READER"))
                .andExpect(jsonPath("$.permissions[0]").value("CATALOG.READ"));
        verify(repository).findActiveActorByExternalSubject("trusted-provider", "mapped-subject");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void unavailableDemoMappingRejectsTheWholeOptionList(int missingIndex) throws Exception {
        mapDemoActors();
        when(repository.findActiveActorByExternalSubject("DEV_EXTERNAL", DEMO_SUBJECTS.get(missingIndex)))
                .thenReturn(Optional.empty());
        expectAuthenticationRequired(mvc(true, "development").perform(get("/api/identity/demo-options")));
        verify(repository).findActiveActorByExternalSubject("DEV_EXTERNAL", DEMO_SUBJECTS.get(missingIndex));
    }

    private static void expectMappedRolesAndPermissions(
            ResultActions result, String prefix, AuthenticatedActor expected) throws Exception {
        List<String> roles = expected.roles().stream().sorted().toList();
        List<String> permissions = expected.permissions().stream().sorted().toList();
        result.andExpect(jsonPath(prefix + ".roles.length()").value(roles.size()))
                .andExpect(jsonPath(prefix + ".permissions.length()").value(permissions.size()));
        for (int index = 0; index < roles.size(); index++) {
            result.andExpect(jsonPath(prefix + ".roles[" + index + "]").value(roles.get(index)));
        }
        for (int index = 0; index < permissions.size(); index++) {
            result.andExpect(jsonPath(prefix + ".permissions[" + index + "]").value(permissions.get(index)));
        }
    }

    private static void expectAuthenticationRequired(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(jsonPath("$.traceId").isEmpty()).andExpect(jsonPath("$.evidenceId").isEmpty());
    }
}