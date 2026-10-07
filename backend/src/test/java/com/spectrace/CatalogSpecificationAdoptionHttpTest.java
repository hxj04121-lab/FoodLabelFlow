package com.spectrace;

import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogCommands.AdoptSpecification;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.interfaces.web.CatalogController;
import com.spectrace.catalog.interfaces.web.CatalogErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP mapping and error envelopes; domain/adoption persistence are tested separately. */
class CatalogSpecificationAdoptionHttpTest {
    private static final String PRODUCT = "product_adoption_test";
    private static final String PATH = "/api/catalog/products/" + PRODUCT + "/formula-adoptions";
    private static final String SOURCE = "formula_adoption_v1";
    private static final String TARGET = "specification_soy_v2";
    private static final String REQUEST = """
            {
              "sourceFormulaVersionId": "formula_adoption_v1",
              "targetSpecificationVersionId": "specification_soy_v2"
            }
            """;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CatalogService service = mock(CatalogService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CatalogController(service))
            .setControllerAdvice(new CatalogErrors())
            .build();

    @Test
    void mapsExplicitAdoptionRequestAndReturnsCreatedCatalogSnapshot() throws Exception {
        Map<String, Object> formula = Map.of(
                "formula_version_id", "formula_adoption_v2",
                "product_id", PRODUCT,
                "version_number", 2,
                "lifecycle_status", "RELEASED",
                "is_current_released", "Y",
                "data_provenance_id", "provenance_soy_v2",
                "items", List.of(Map.of(
                        "supplier_material_id", "material_chocolate_base",
                        "specification_version_id", TARGET,
                        "quantity_value", new BigDecimal("20.5000"),
                        "quantity_unit", "g")));
        when(service.adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET)))
                .thenReturn(formula);

        MvcResult result = mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.formula_version_id").value("formula_adoption_v2"))
                .andExpect(jsonPath("$.product_id").value(PRODUCT))
                .andExpect(jsonPath("$.version_number").value(2))
                .andExpect(jsonPath("$.lifecycle_status").value("RELEASED"))
                .andExpect(jsonPath("$.is_current_released").value("Y"))
                .andExpect(jsonPath("$.items[0].specification_version_id").value(TARGET))
                .andReturn();

        assertThat(JSON.readTree(result.getResponse().getContentAsString()))
                .isEqualTo(JSON.readTree(JSON.writeValueAsString(formula)));
        verify(service).adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"sourceFormulaVersionId\":\"formula_adoption_v1\"}",
            "{\"targetSpecificationVersionId\":\"specification_soy_v2\"}",
            "{\"sourceFormulaVersionId\":\"\",\"targetSpecificationVersionId\":\"specification_soy_v2\"}",
            "{\"sourceFormulaVersionId\":\"formula_adoption_v1\",\"targetSpecificationVersionId\":null}",
            "{\"sourceFormulaVersionId\":{},\"targetSpecificationVersionId\":\"specification_soy_v2\"}",
            "{",
            "[]",
            "null"
    })
    void rejectsMissingRequiredFieldsAndMalformedJsonBeforeCallingTheService(String request)
            throws Exception {
        MvcResult result = mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertCanonicalError(result, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingRequestBodyBeforeCallingTheService() throws Exception {
        MvcResult result = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertCanonicalError(result, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("businessFailures")
    void preservesBusinessStatusAndCanonicalErrorEnvelope(int statusCode, String errorCode)
            throws Exception {
        String message = "Adoption cannot proceed with this request";
        when(service.adoptSpecification(eq(PRODUCT), any(AdoptSpecification.class)))
                .thenThrow(new CatalogFailure(statusCode, errorCode, message));

        MvcResult result = mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().is(statusCode))
                .andReturn();

        JsonNode error = assertCanonicalError(result, errorCode);
        assertThat(error.get("message").stringValue()).isEqualTo(message);
        verify(service).adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET));
    }

    @Test
    void auditAdapterFailureReturnsGenericErrorWithoutInternalDetails() throws Exception {
        String privateDetails = "Audit write failed: private-db audit_event connection details";
        when(service.adoptSpecification(eq(PRODUCT), any(AdoptSpecification.class)))
                .thenThrow(new RuntimeException(privateDetails));

        MvcResult result = mvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isInternalServerError())
                .andReturn();

        JsonNode error = assertCanonicalError(result, "INTERNAL_ERROR");
        assertThat(error.get("message").stringValue())
                .isEqualTo("The catalog request could not be completed");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(privateDetails, "private-db", "audit_event", "RuntimeException");
        verify(service).adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET));
    }

    @Test
    void catalogQueryTypeMismatchKeepsBadRequestAndCanonicalError() throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog/products").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertCanonicalError(result, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedAdoptionContentTypeKeepsFrameworkMediaTypeStatus() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content(REQUEST))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(service);
    }

    @Test
    void unsupportedAdoptionMethodKeepsFrameworkMethodStatus() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }

    private JsonNode assertCanonicalError(MvcResult result, String code) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode error = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(error.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("code", "message", "traceId", "evidenceId");
        assertThat(error.get("code").stringValue()).isEqualTo(code);
        assertThat(error.get("message").stringValue()).isNotBlank();
        assertThat(error.get("traceId").isNull()).isTrue();
        assertThat(error.get("evidenceId").isNull()).isTrue();
        return error;
    }

    private static Stream<Arguments> businessFailures() {
        return Stream.of(
                Arguments.of(401, "AUTHENTICATION_REQUIRED"),
                Arguments.of(403, "AUTHORIZATION_DENIED"),
                Arguments.of(404, "RESOURCE_NOT_FOUND"),
                Arguments.of(409, "CURRENT_FORMULA_CHANGED"),
                Arguments.of(422, "SPECIFICATION_NOT_RELEASED"),
                Arguments.of(503, "CATALOG_INTEGRATION_UNAVAILABLE"));
    }
}
