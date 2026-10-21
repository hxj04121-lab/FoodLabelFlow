package com.spectrace.catalog.application;

import com.spectrace.catalog.domain.CatalogCommands.AdoptSpecification;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchSpecificationAdoptionServiceTest {
    private static final String TARGET = "spec_target_v2";
    private static final String PRODUCT = "product-1";
    private static final String SOURCE = "formula-1";
    private static final String RESULT = "formula-2";

    @Test
    void commitsOnePerProductAndStoresTheReplayMarker() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1"))
                .thenReturn(List.of(Map.of("product_id", PRODUCT)));
        when(store.findBatchAdoption(PRODUCT, TARGET)).thenReturn(null);
        when(store.get(CatalogStore.Kind.PRODUCT, PRODUCT, true))
                .thenReturn(Map.of("current_formula_version_id", SOURCE));
        when(catalog.adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET)))
                .thenReturn(Map.of("formula_version_id", RESULT));

        var summary = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(summary.targetSpecificationVersionId()).isEqualTo(TARGET);
        assertThat(summary.candidateCount()).isEqualTo(1);
        assertThat(summary.adoptedCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        assertThat(summary.results()).containsExactly(new BatchSpecificationAdoptionService.Result(
                PRODUCT, "ADOPTED", RESULT, false, null, null));
        verify(catalog).requireBatchAdoptionPermission();
        // The product lock precedes the first nonlocking read, which fixes the REPEATABLE READ snapshot.
        var order = inOrder(store, catalog);
        order.verify(store).get(CatalogStore.Kind.PRODUCT, PRODUCT, true);
        order.verify(store).findBatchAdoption(PRODUCT, TARGET);
        order.verify(catalog).adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET));
        verify(store).recordBatchAdoption(PRODUCT, SOURCE, TARGET, RESULT);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void replaysTheStoredFormulaWithoutCallingAdoptionAgain() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1"))
                .thenReturn(List.of(Map.of("product_id", PRODUCT)));
        when(store.findBatchAdoption(PRODUCT, TARGET))
                .thenReturn(Map.of("formula_version_id", RESULT, "source_formula_version_id", SOURCE));

        var summary = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(summary.adoptedCount()).isEqualTo(1);
        assertThat(summary.results().getFirst().formulaVersionId()).isEqualTo(RESULT);
        assertThat(summary.results().getFirst().replayed()).isTrue();
        verify(catalog, never()).adoptSpecification(anyString(), any());
        verify(store, never()).recordBatchAdoption(anyString(), anyString(), anyString(), anyString());
        verify(transactions).commit(any());
    }

    @Test
    void rejectsMissingAdminPermissionsBeforeReadingCatalogCandidates() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        doThrow(new CatalogFailure(403, "AUTHORIZATION_DENIED", "Required catalog permissions are missing"))
                .when(catalog).requireBatchAdoptionPermission();

        assertThatThrownBy(() -> new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET))
                .isInstanceOfSatisfying(CatalogFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(403);
                    assertThat(failure.code()).isEqualTo("AUTHORIZATION_DENIED");
                });

        verify(catalog).requireBatchAdoptionPermission();
        verifyNoInteractions(store, transactions);
    }

    @Test
    void rejectsAnUnreleasedTargetBeforeCandidateDiscoveryEvenWhenTheBatchWouldBeEmpty() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        Map<String, Object> target = Map.of("supplier_material_id", "material-1");
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false)).thenReturn(target);
        doThrow(new CatalogFailure(422, "SPECIFICATION_NOT_RELEASED", "Target must be released"))
                .when(catalog).requireBatchAdoptionTarget(target);

        assertThatThrownBy(() -> new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET))
                .isInstanceOfSatisfying(CatalogFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("SPECIFICATION_NOT_RELEASED");
                });

        verify(catalog).requireBatchAdoptionPermission();
        verify(catalog).requireBatchAdoptionTarget(target);
        verify(store).get(CatalogStore.Kind.SPECIFICATION, TARGET, false);
        verify(store, never()).currentProductsUsingMaterial(anyString());
        verifyNoInteractions(transactions);
    }

    @Test
    void rollsBackAProductWhenRecordingItsReplayMarkerFails() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1"))
                .thenReturn(List.of(Map.of("product_id", PRODUCT)));
        when(store.findBatchAdoption(PRODUCT, TARGET)).thenReturn(null);
        when(store.get(CatalogStore.Kind.PRODUCT, PRODUCT, true))
                .thenReturn(Map.of("current_formula_version_id", SOURCE));
        when(catalog.adoptSpecification(PRODUCT, new AdoptSpecification(SOURCE, TARGET)))
                .thenReturn(Map.of("formula_version_id", RESULT));
        doThrow(new IllegalStateException("simulated marker insert failure"))
                .when(store).recordBatchAdoption(PRODUCT, SOURCE, TARGET, RESULT);

        var summary = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.results().getFirst().status()).isEqualTo("FAILED");
        assertThat(summary.results().getFirst().errorCode()).isEqualTo("ADOPTION_FAILED");
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    void adoptsFromTheFormulaThatIsCurrentWhenTheProductIsLocked() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1"))
                .thenReturn(List.of(Map.of("product_id", PRODUCT)));
        when(store.findBatchAdoption(PRODUCT, TARGET)).thenReturn(null);
        when(store.get(CatalogStore.Kind.PRODUCT, PRODUCT, true))
                .thenReturn(Map.of("current_formula_version_id", "formula-current-at-lock"));
        when(catalog.adoptSpecification(PRODUCT, new AdoptSpecification("formula-current-at-lock", TARGET)))
                .thenReturn(Map.of("formula_version_id", RESULT));

        var summary = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(summary.adoptedCount()).isEqualTo(1);
        verify(catalog).adoptSpecification(PRODUCT, new AdoptSpecification("formula-current-at-lock", TARGET));
    }

    @Test
    void rejectsBlankAndOverlongTargetIdsBeforePermissionOrCatalogAccess() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var service = new BatchSpecificationAdoptionService(store, catalog, transactions);

        assertThatThrownBy(() -> service.adopt("  ")).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> service.adopt("x".repeat(101))).isInstanceOf(CatalogFailure.class);

        verifyNoInteractions(store, catalog, transactions);
    }

    @Test
    void skipsProductsWithoutCurrentFormulaAndReturnsAnEmptySummaryForNoCandidates() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1"))
                .thenReturn(List.of(Map.of("product_id", PRODUCT)));
        when(store.findBatchAdoption(PRODUCT, TARGET)).thenReturn(null);
        when(store.get(CatalogStore.Kind.PRODUCT, PRODUCT, true))
                .thenReturn(java.util.Collections.singletonMap("current_formula_version_id", null));

        var skipped = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(skipped.skippedCount()).isEqualTo(1);
        assertThat(skipped.results().getFirst()).isEqualTo(new BatchSpecificationAdoptionService.Result(
                PRODUCT, "SKIPPED", null, false, "NO_CURRENT_FORMULA", "Product has no current released formula."));
        verify(transactions).getTransaction(any());

        when(store.currentProductsUsingMaterial("material-1")).thenReturn(List.of());
        var empty = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(" " + TARGET + " ");
        assertThat(empty.targetSpecificationVersionId()).isEqualTo(TARGET);
        assertThat(empty.candidateCount()).isZero();
        assertThat(empty.results()).isEmpty();
    }

    @Test
    void resolvesConcurrentReplayAndSeparatesSkippedCatalogConflictsFromFailures() {
        CatalogStore store = mock(CatalogStore.class);
        CatalogService catalog = mock(CatalogService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(store.get(CatalogStore.Kind.SPECIFICATION, TARGET, false))
                .thenReturn(Map.of("supplier_material_id", "material-1"));
        when(store.currentProductsUsingMaterial("material-1")).thenReturn(List.of(
                Map.of("product_id", "product-race"), Map.of("product_id", "product-skip"),
                Map.of("product_id", "product-fail")));
        when(store.findBatchAdoption("product-race", TARGET)).thenReturn(null)
                .thenReturn(Map.of("formula_version_id", "formula-raced", "source_formula_version_id", "formula-old"));
        when(store.findBatchAdoption("product-skip", TARGET)).thenReturn(null).thenReturn(null);
        when(store.findBatchAdoption("product-fail", TARGET)).thenReturn(null).thenReturn(null);
        for (String productId : List.of("product-race", "product-skip", "product-fail")) {
            when(store.get(CatalogStore.Kind.PRODUCT, productId, true))
                    .thenReturn(Map.of("current_formula_version_id", "formula-old"));
        }
        when(catalog.adoptSpecification("product-race", new AdoptSpecification("formula-old", TARGET)))
                .thenThrow(new CatalogFailure(409, "FORMULA_CHANGED", "Formula changed"));
        when(catalog.adoptSpecification("product-skip", new AdoptSpecification("formula-old", TARGET)))
                .thenThrow(new CatalogFailure(422, "SPECIFICATION_INCOMPATIBLE", "Target is incompatible"));
        when(catalog.adoptSpecification("product-fail", new AdoptSpecification("formula-old", TARGET)))
                .thenThrow(new CatalogFailure(403, "AUTHORIZATION_DENIED", "Permission revoked"));

        var summary = new BatchSpecificationAdoptionService(store, catalog, transactions).adopt(TARGET);

        assertThat(summary.adoptedCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.results()).containsExactly(
                new BatchSpecificationAdoptionService.Result("product-race", "ADOPTED", "formula-raced", true, null, null),
                new BatchSpecificationAdoptionService.Result("product-skip", "SKIPPED", null, false,
                        "SPECIFICATION_INCOMPATIBLE", "Target is incompatible"),
                new BatchSpecificationAdoptionService.Result("product-fail", "FAILED", null, false,
                        "AUTHORIZATION_DENIED", "Adoption failed and was rolled back; retry after resolving the error."));
    }
}
