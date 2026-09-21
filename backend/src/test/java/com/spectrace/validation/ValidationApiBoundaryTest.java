package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.validation.application.ValidationApplicationService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.ValidationRunQueryService;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import com.spectrace.validation.interfaces.web.ValidationApiErrors;
import com.spectrace.validation.interfaces.web.ValidationController;
import com.spectrace.allergen.interfaces.web.AllergenController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ValidationApiBoundaryTest {
    private static final String LABEL = "label-1";
    private static final String RULE_SET = "rules-1";
    private static final String RUN = "run-1";

    @Mock
    private ValidationApplicationService validation;
    @Mock
    private ValidationRunQueryService queries;
    @Mock
    private AllergenFactsPort allergens;
    @Mock
    private ValidationIntegration integration;

    private MockMvc mvc;
    private ValidationRun run;
    private ValidationRunQueryService.ValidationRunDetails details;

    @BeforeEach
    void setUp() {
        run = new ValidationRun(RUN, LABEL, RULE_SET, ValidationStatus.PASSED,
                "actor-1", Instant.parse("2026-09-21T05:00:00Z"), null, "prov-1");
        var result = new ValidationResult("result-1", RUN, "rule-1", "RULE_PASSED",
                ValidationSeverity.INFO, true, false, "passed");
        details = new ValidationRunQueryService.ValidationRunDetails(run, List.of(result));
        mvc = MockMvcBuilders.standaloneSetup(
                        new ValidationController(validation, queries),
                        new AllergenController(allergens, integration))
                .setControllerAdvice(new ValidationApiErrors())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        JsonMapper.builder()
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                                .build()))
                .build();
    }

    @Test
    void mapsExplicitDtosForAllEndpoints() throws Exception {
        when(validation.validate(LABEL, RULE_SET)).thenReturn(run);
        when(queries.find(RUN)).thenReturn(details);
        when(allergens.listAllergens("US")).thenReturn(List.of(
                new AllergenEntry("all-1", "SOY", "Soy", "US")));

        mvc.perform(get("/api/v1/allergens").param("jurisdictionCode", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].allergenId").value("all-1"))
                .andExpect(jsonPath("$[0].allergenCode").value("SOY"))
                .andExpect(jsonPath("$[0].displayName").value("Soy"))
                .andExpect(jsonPath("$[0].jurisdictionCode").value("US"));

        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationRunId").value(RUN))
                .andExpect(jsonPath("$.labelVersionId").value(LABEL))
                .andExpect(jsonPath("$.ruleSetVersionId").value(RULE_SET))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.ranAt").value("2026-09-21T05:00:00Z"))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].ruleDefinitionId").value("rule-1"))
                .andExpect(jsonPath("$.results[0].resultCode").value("RULE_PASSED"));

        mvc.perform(get("/api/v1/validation-runs/{id}", RUN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationRunId").value(RUN))
                .andExpect(jsonPath("$.results", hasSize(1)));
    }

    @Test
    void rejectsMissingOrForgedRequestFieldsBeforeApplicationInvocation() throws Exception {
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\",\"actorId\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(validation, never()).validate(anyString(), anyString());
    }

    @Test
    void mapsAuthenticationAuthorizationAndBusinessFailuresToStableErrors() throws Exception {
        doThrow(new UnknownIdentityException("internal identity detail"))
                .when(validation).validate(LABEL, RULE_SET);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        doThrow(new AuthorizationDeniedException("internal permission detail"))
                .when(validation).validate(LABEL, RULE_SET);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));

        doThrow(ValidationFailure.notCurrent("stale label"))
                .when(validation).validate(LABEL, RULE_SET);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LABEL_VERSION_NOT_CURRENT"));
    }

    @Test
    void hidesUnexpectedFailureDetailsAndMapsMissingRun() throws Exception {
        doThrow(new IllegalStateException("SQL table secret"))
                .when(validation).validate(LABEL, RULE_SET);
        mvc.perform(post("/api/v1/label-versions/{id}/validation-runs", LABEL)
                        .contentType("application/json")
                        .content("{\"ruleSetVersionId\":\"" + RULE_SET + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("SQL"))));

        doThrow(ValidationFailure.notFound("run missing"))
                .when(queries).find(RUN);
        mvc.perform(get("/api/v1/validation-runs/{id}", RUN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void allergenReadUsesTrustedIdentityAndPermission() throws Exception {
        doThrow(new UnknownIdentityException("missing headers"))
                .when(integration).requireActor(ValidationOrchestrator.VALIDATE_PERMISSION);
        mvc.perform(get("/api/v1/allergens").param("jurisdictionCode", "US"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        doThrow(new AuthorizationDeniedException("no permission"))
                .when(integration).requireActor(ValidationOrchestrator.VALIDATE_PERMISSION);
        mvc.perform(get("/api/v1/allergens").param("jurisdictionCode", "US"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
    }
}
