package com.spectrace.impact.application;

import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import com.spectrace.catalog.application.port.RelevantProductLookupPort.RelevantProduct;
import com.spectrace.impact.support.InMemoryImpactPorts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelevantProductDiscoveryTest {
    private static final String MATERIAL = "mat_chocolate_base";

    private final InMemoryImpactPorts.RelevantProducts lookup = new InMemoryImpactPorts.RelevantProducts();
    private final RelevantProductDiscovery discovery = new RelevantProductDiscovery(lookup);

    @Test
    void returnsEveryLabelledProductFromTheLookupOrderedByProduct() {
        lookup.add(MATERIAL, product("prod_b", "label_b_v1", "fi_b_v1_1", "fi_b_v1_4"))
                .add(MATERIAL, product("prod_a", "label_a_v1", "fi_a_v1_1"))
                .add("mat_soy_carrier", product("prod_c", "label_c_v1", "fi_c_v1_2"));

        assertThat(discovery.discover(MATERIAL)).containsExactly(
                new RelevantProductTarget("prod_a", "formula_prod_a_v1", "label_a_v1", List.of("fi_a_v1_1")),
                new RelevantProductTarget("prod_b", "formula_prod_b_v1", "label_b_v1",
                        List.of("fi_b_v1_1", "fi_b_v1_4")));
    }

    @Test
    void aMaterialNoCurrentFormulaUsesIsACompletedEmptyResult() {
        lookup.add("mat_soy_carrier", product("prod_c", "label_c_v1", "fi_c_v1_2"));

        assertThat(discovery.discover(MATERIAL)).isEmpty();
    }

    @Test
    void aRelevantProductWithoutAPublishedLabelFailsTheWholeDiscovery() {
        lookup.add(MATERIAL, product("prod_a", "label_a_v1", "fi_a_v1_1"))
                .add(MATERIAL, product("prod_z", null, "fi_z_v1_1"))
                .add(MATERIAL, product("prod_m", null, "fi_m_v1_1"));

        assertThatThrownBy(() -> discovery.discover(MATERIAL))
                .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("PUBLISHED_LABEL_MISSING");
                    assertThat(failure.getMessage()).endsWith(": prod_m, prod_z");
                });
    }

    @Test
    void aDuplicatedProductFromTheLookupIsAServerFaultNotAMerge() {
        RelevantProductLookupPort duplicating = material -> List.of(
                product("prod_a", "label_a_v1", "fi_a_v1_1"),
                product("prod_a", "label_a_v1", "fi_a_v1_2"));

        assertThatIllegalStateException()
                .isThrownBy(() -> new RelevantProductDiscovery(duplicating).discover(MATERIAL))
                .withMessageContaining("prod_a");
    }

    @Test
    void theMaterialIsRequiredAndPassedToTheLookupUnchanged() {
        var requested = new ArrayList<String>();
        var discovering = new RelevantProductDiscovery(material -> {
            requested.add(material);
            return List.of();
        });

        assertThatIllegalArgumentException().isThrownBy(() -> discovering.discover(" "));
        assertThat(discovering.discover(MATERIAL)).isEmpty();
        assertThat(requested).containsExactly(MATERIAL);
    }

    @Test
    void aTargetAlwaysCarriesALabelAndAMatchingItem() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RelevantProductTarget("prod_a", "formula_a_v1", null, List.of("fi_a_v1_1")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RelevantProductTarget("prod_a", "formula_a_v1", "label_a_v1", List.of()));
    }

    private static RelevantProduct product(String productId, String labelVersionId, String... formulaItemIds) {
        return new RelevantProduct(productId, "formula_" + productId + "_v1", labelVersionId, List.of(formulaItemIds));
    }
}
