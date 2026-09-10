package com.spectrace;

import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.interfaces.web.CatalogErrors;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.UnknownIdentityException;
import com.spectrace.identity.interfaces.web.IdentityErrors;
import com.spectrace.shared.api.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SharedApiErrorContractTest {

    @Test
    void m1BusinessErrorsUseTheCanonicalEnvelopeWithoutInventingEvidence() {
        ResponseEntity<ApiError> response = new CatalogErrors().business(
                new CatalogFailure(409, "CURRENT_FORMULA_CHANGED", "Refresh before retrying"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertCanonicalShape(response.getBody(), "CURRENT_FORMULA_CHANGED");
    }

    @Test
    void m1ValidationNotFoundAndDuplicateErrorsRemainRepresentable() {
        CatalogErrors errors = new CatalogErrors();

        assertCanonicalShape(errors.business(
                new CatalogFailure(400, "INVALID_REQUEST", "A field is invalid")).getBody(),
                "INVALID_REQUEST");
        assertCanonicalShape(errors.business(
                new CatalogFailure(404, "RESOURCE_NOT_FOUND", "Resource not found")).getBody(),
                "RESOURCE_NOT_FOUND");
        assertCanonicalShape(errors.conflict(null).getBody(), "DATA_CONFLICT");
    }

    @Test
    void m4AndM5KeepAuthenticationAndAuthorizationSemanticsDistinct() {
        IdentityErrors errors = new IdentityErrors();

        ResponseEntity<ApiError> unauthenticated = errors.unauthenticated(
                new UnknownIdentityException("Identity is not mapped"));
        ResponseEntity<ApiError> forbidden = errors.forbidden(
                new AuthorizationDeniedException("Permission denied"));

        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertCanonicalShape(unauthenticated.getBody(), "AUTHENTICATION_REQUIRED");
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertCanonicalShape(forbidden.getBody(), "AUTHORIZATION_DENIED");
    }

    private void assertCanonicalShape(ApiError error, String expectedCode) {
        assertThat(error).isNotNull();
        assertThat(Arrays.stream(ApiError.class.getRecordComponents())
                .map(component -> component.getName())
                .toList()).containsExactly("code", "message", "traceId", "evidenceId");
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.message()).isNotBlank();
        assertThat(error.traceId()).isNull();
        assertThat(error.evidenceId()).isNull();
    }
}
