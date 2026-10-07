package com.spectrace;

import com.spectrace.catalog.application.CatalogIntegration;
import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogCommands.AdoptSpecification;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Day 4 adoption rules, isolated from JDBC and the authenticated HTTP fixture. */
class CatalogSpecificationAdoptionTest {
    private static final String PRODUCT_ID = "product-a";
    private static final String SOURCE_ID = "formula-a-v1";
    private static final String TARGET_ID = "spec-chocolate-v2";
    private static final String NEW_ID = "formula-a-v2";
    private static final String PROVENANCE = "target-spec-evidence";

    private CatalogStore store;
    private CatalogIntegration integration;
    private CatalogService service;
    private Map<String, Object> product;
    private Map<String, Object> source;
    private Map<String, Object> target;
    private Map<String, Object> previousSpecification;
    private Map<String, Object> otherSpecification;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void fixture() {
        store = mock(CatalogStore.class);
        integration = mock(CatalogIntegration.class);
        var provider = (ObjectProvider<CatalogIntegration>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(integration);
        service = new CatalogService(store, provider);
        when(integration.requireActor(anyString())).thenReturn("user_admin");
        product = row("product_id", PRODUCT_ID, "current_formula_version_id", SOURCE_ID);
        source = row("formula_version_id", SOURCE_ID, "product_id", PRODUCT_ID,
                "lifecycle_status", "RELEASED", "is_current_released", "Y", "version_number", 1,
                "data_provenance_id", "old-evidence");
        target = specification("mat-chocolate", 2, "RELEASED", LocalDate.of(2020, 1, 1));
        previousSpecification = specification("mat-chocolate", 1, "RELEASED", LocalDate.of(2020, 1, 1));
        otherSpecification = specification("mat-soy", 1, "RELEASED", LocalDate.of(2020, 1, 1));
        doReturn(product).when(store).get(eq(PRODUCT), eq(PRODUCT_ID), anyBoolean());
        doReturn(source).when(store).get(eq(FORMULA), eq(SOURCE_ID), anyBoolean());
        when(store.get(eq(SPECIFICATION), eq(TARGET_ID), anyBoolean())).thenReturn(target);
        when(store.get(eq(SPECIFICATION), eq("spec-chocolate-v1"), anyBoolean())).thenReturn(previousSpecification);
        when(store.get(eq(SPECIFICATION), eq("spec-soy-v1"), anyBoolean())).thenReturn(otherSpecification);
        when(store.formulaItems(SOURCE_ID)).thenReturn(List.of(
                item("mat-chocolate", "spec-chocolate-v1", 1, new BigDecimal("1.2500"), "kg"),
                item("mat-soy", "spec-soy-v1", 4, null, null),
                item("mat-chocolate", "spec-chocolate-v1", 9, new BigDecimal("2.5000"), "g")));
    }

    @Test
    void commandRejectsMissingBlankAndOversizedReferences() {
        assertFailure(() -> new AdoptSpecification(null, TARGET_ID), 400, "INVALID_REQUEST");
        assertFailure(() -> new AdoptSpecification(" ", TARGET_ID), 400, "INVALID_REQUEST");
        assertFailure(() -> new AdoptSpecification("x".repeat(121), TARGET_ID), 400, "INVALID_REQUEST");
        assertFailure(() -> new AdoptSpecification(SOURCE_ID, null), 400, "INVALID_REQUEST");
        assertFailure(() -> new AdoptSpecification(SOURCE_ID, " "), 400, "INVALID_REQUEST");
        assertFailure(() -> new AdoptSpecification(SOURCE_ID, "x".repeat(101)), 400, "INVALID_REQUEST");
        assertThat(new AdoptSpecification(" " + SOURCE_ID + " ", " " + TARGET_ID + " "))
                .isEqualTo(new AdoptSpecification(SOURCE_ID, TARGET_ID));
    }

    @Test
    void missingRequestAndInvalidProductAreRejectedBeforePersistence() {
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, null), 400, "INVALID_REQUEST");
        assertFailure(() -> service.adoptSpecification(null, request()), 400, "INVALID_REQUEST");
        assertFailure(() -> service.adoptSpecification(" ", request()), 400, "INVALID_REQUEST");
        assertThatThrownBy(() -> service.adoptSpecification("x".repeat(101), request()))
                .isInstanceOf(CatalogFailure.class);
        verifyNoInteractions(store);
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingIdentityAndAuditAdapterFailsClosed() {
        var provider = (ObjectProvider<CatalogIntegration>) mock(ObjectProvider.class);
        var unavailable = new CatalogService(store, provider);
        assertFailure(() -> unavailable.adoptSpecification(PRODUCT_ID, request()),
                503, "CATALOG_INTEGRATION_UNAVAILABLE");
        verifyNoInteractions(store);
    }

    @Test
    void requiresBothDataMaintenanceAndFormulaReleaseBeforeReadingOrWriting() {
        when(integration.requireActor("FORMULA.RELEASE"))
                .thenThrow(new CatalogFailure(403, "AUTHORIZATION_DENIED", "Denied"));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 403, "AUTHORIZATION_DENIED");
        verify(integration).requireActor("DATA.MAINTAIN");
        verify(integration).requireActor("FORMULA.RELEASE");
        verifyNoInteractions(store);
        verify(integration, never()).audit(anyString(), anyString(), anyString(), anyString());
        verify(integration, never()).auditSpecificationAdoption(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void missingProductStaysNotFoundAndMissingBodyReferenceIsInvalidReference() {
        when(store.get(eq(PRODUCT), eq(PRODUCT_ID), anyBoolean()))
                .thenThrow(new CatalogFailure(404, "RESOURCE_NOT_FOUND", "Product not found"));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 404, "RESOURCE_NOT_FOUND");
        noAdoptionWrite();
        doReturn(product).when(store).get(eq(PRODUCT), eq(PRODUCT_ID), anyBoolean());
        when(store.get(eq(FORMULA), eq(SOURCE_ID), anyBoolean()))
                .thenThrow(new CatalogFailure(404, "RESOURCE_NOT_FOUND", "Formula not found"));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "INVALID_REFERENCE");
        noAdoptionWrite();
        doReturn(source).when(store).get(eq(FORMULA), eq(SOURCE_ID), anyBoolean());
        when(store.get(eq(SPECIFICATION), eq(TARGET_ID), anyBoolean()))
                .thenThrow(new CatalogFailure(404, "RESOURCE_NOT_FOUND", "Specification not found"));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "INVALID_REFERENCE");
        noAdoptionWrite();
    }

    @Test
    void sourceMustBelongToTheRequestedProduct() {
        source.put("product_id", "another-product");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()),
                422, "SOURCE_FORMULA_PRODUCT_MISMATCH");
        noAdoptionWrite();
    }

    @Test
    void sourceMustBeReleased() {
        source.put("lifecycle_status", "DRAFT");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "SOURCE_FORMULA_NOT_RELEASED");
        noAdoptionWrite();
    }

    @Test
    void staleSourceCannotReplaceTheCurrentFormula() {
        product.put("current_formula_version_id", "new-current-formula");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 409, "CURRENT_FORMULA_CHANGED");
        noAdoptionWrite();
    }

    @Test
    void currentPointerWithAnInconsistentSourceFlagCannotBeAdopted() {
        source.put("is_current_released", "N");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 409, "CURRENT_FORMULA_CHANGED");
        noAdoptionWrite();
    }

    @Test
    void sourceWithoutItemsCannotCreateAnEmptySnapshot() {
        when(store.formulaItems(SOURCE_ID)).thenReturn(List.of());
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 400, "INVALID_REQUEST");
        noAdoptionWrite();
    }

    @Test
    void targetMustBeReleasedAndEffective() {
        target.put("lifecycle_status", "DRAFT");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "SPECIFICATION_NOT_RELEASED");
        noAdoptionWrite();
        target.put("lifecycle_status", "RELEASED");
        target.put("effective_date", Date.valueOf(LocalDate.now().plusYears(2)));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "SPECIFICATION_NOT_EFFECTIVE");
        noAdoptionWrite();
    }

    @Test
    void targetMaterialMustOccurInTheSourceFormula() {
        target.put("supplier_material_id", "mat-not-in-source");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        noAdoptionWrite();
    }

    @Test
    void untouchedSpecificationMustRemainReleasedAndEffective() {
        otherSpecification.put("lifecycle_status", "RETIRED");
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "SPECIFICATION_NOT_RELEASED");
        noAdoptionWrite();
        otherSpecification.put("lifecycle_status", "RELEASED");
        otherSpecification.put("effective_date", Date.valueOf(LocalDate.now().plusYears(2)));
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()), 422, "SPECIFICATION_NOT_EFFECTIVE");
        noAdoptionWrite();
    }

    @Test
    void alreadyAdoptedOrOlderSpecificationCannotCreateANewVersion() {
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID,
                new AdoptSpecification(SOURCE_ID, "spec-chocolate-v1")), 409, "SPECIFICATION_ALREADY_ADOPTED");
        noAdoptionWrite();
        previousSpecification.put("version_number", 3);
        assertFailure(() -> service.adoptSpecification(PRODUCT_ID, request()),
                422, "SPECIFICATION_VERSION_NOT_NEWER");
        noAdoptionWrite();
    }

    @Test
    void validAdoptionCreatesAndReleasesANewSnapshotUsingTargetEvidence() {
        var adopted = stubAdoptedSnapshot();
        var result = service.adoptSpecification(PRODUCT_ID, request());

        assertThat(result).containsAllEntriesOf(adopted);
        assertThat(result.get("items")).isEqualTo(store.formulaItems(NEW_ID));
        verify(store).adoptFormula(PRODUCT_ID, SOURCE_ID, "mat-chocolate", TARGET_ID, PROVENANCE, "user_admin");
        verify(store).releaseFormula(NEW_ID, PRODUCT_ID, "user_admin");
        verify(integration).audit("user_admin", "FORMULA_CREATED", NEW_ID, PROVENANCE);
        verify(integration).audit("user_admin", "FORMULA_RELEASED", NEW_ID, PROVENANCE);
        verify(integration).auditSpecificationAdoption("user_admin", NEW_ID, SOURCE_ID, TARGET_ID, PROVENANCE);
    }

    @Test
    void releasedTargetCanReplaceARetiredHistoricalSpecification() {
        previousSpecification.put("lifecycle_status", "RETIRED");
        var adopted = stubAdoptedSnapshot();

        assertThat(service.adoptSpecification(PRODUCT_ID, request())).containsAllEntriesOf(adopted);

        verify(store).adoptFormula(PRODUCT_ID, SOURCE_ID, "mat-chocolate", TARGET_ID, PROVENANCE, "user_admin");
        verify(store).releaseFormula(NEW_ID, PRODUCT_ID, "user_admin");
    }

    private Map<String, Object> stubAdoptedSnapshot() {
        var adopted = row("formula_version_id", NEW_ID, "product_id", PRODUCT_ID, "version_number", 2,
                "lifecycle_status", "RELEASED", "is_current_released", "Y", "data_provenance_id", PROVENANCE);
        when(store.adoptFormula(PRODUCT_ID, SOURCE_ID, "mat-chocolate", TARGET_ID, PROVENANCE, "user_admin"))
                .thenReturn(NEW_ID);
        when(store.get(eq(FORMULA), eq(NEW_ID), anyBoolean())).thenReturn(adopted);
        var adoptedItems = List.of(item("mat-chocolate", TARGET_ID, 1, new BigDecimal("1.2500"), "kg"),
                item("mat-soy", "spec-soy-v1", 4, null, null),
                item("mat-chocolate", TARGET_ID, 9, new BigDecimal("2.5000"), "g"));
        when(store.formulaItems(NEW_ID)).thenReturn(adoptedItems);
        return adopted;
    }

    private AdoptSpecification request() { return new AdoptSpecification(SOURCE_ID, TARGET_ID); }

    private void noAdoptionWrite() {
        verify(store, never()).adoptFormula(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(store, never()).releaseFormula(anyString(), anyString(), anyString());
        verify(integration, never()).audit(anyString(), anyString(), anyString(), anyString());
        verify(integration, never()).auditSpecificationAdoption(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static Map<String, Object> specification(String material, int version, String state, LocalDate date) {
        return row("supplier_material_id", material, "version_number", version, "lifecycle_status", state,
                "effective_date", Date.valueOf(date), "data_provenance_id", PROVENANCE);
    }

    private static Map<String, Object> item(String material, String specification, int sequence,
                                             BigDecimal quantity, String unit) {
        return row("supplier_material_id", material, "specification_version_id", specification,
                "sequence_no", sequence, "quantity_value", quantity, "quantity_unit", unit);
    }

    private static Map<String, Object> row(Object... entries) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) row.put((String) entries[i], entries[i + 1]);
        return row;
    }

    private static void assertFailure(Runnable operation, int status, String code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CatalogFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(status);
            assertThat(failure.code()).isEqualTo(code);
        });
    }
}
