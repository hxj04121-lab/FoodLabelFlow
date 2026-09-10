package com.spectrace;

import com.spectrace.catalog.application.CatalogIntegration;
import com.spectrace.catalog.application.CatalogService;
import com.spectrace.catalog.domain.CatalogCommands.*;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogRulesTest {
    @Test void rejectsEmptyFormulaAndBlankBusinessKeys() {
        assertThatThrownBy(() -> new Formula("product", "provenance", List.of())).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> new Supplier(" ", "Name", "prov")).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> new Material("supplier", null, "code", "name", "x".repeat(601), "prov"))
                .isInstanceOf(CatalogFailure.class);
    }
    @Test void validatesQuantityWithoutRoundingOrInventingUnitConversions() {
        assertThatThrownBy(() -> new Item("m", "s", BigDecimal.ZERO, "kg")).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> new Item("m", "s", new BigDecimal("1.00001"), "kg")).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> new Item("m", "s", new BigDecimal("100000000"), "kg")).isInstanceOf(CatalogFailure.class);
        assertThatThrownBy(() -> new Item("m", "s", null, "kg")).isInstanceOf(CatalogFailure.class);
        assertThat(new Item("m", "s", null, null).quantity()).isNull();
        assertThat(new Item("m", "s", new BigDecimal("1.25"), "kg").quantity()).isEqualByComparingTo("1.25");
    }
    @SuppressWarnings("unchecked")
    @Test void missingIntegrationFailsClosedBeforeDatabaseWrite() {
        var store = mock(CatalogStore.class);
        var provider = (ObjectProvider<CatalogIntegration>) mock(ObjectProvider.class);
        var service = new CatalogService(store, provider);
        assertThatThrownBy(() -> service.create(new Supplier("code", "name", "prov")))
                .isInstanceOfSatisfying(CatalogFailure.class, e -> assertThat(e.status()).isEqualTo(503));
        verifyNoInteractions(store);
    }
}
