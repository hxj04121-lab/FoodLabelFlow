package com.spectrace.allergen;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FormulaAllergenDerivationIntegrationTest extends MySqlIntegrationTestSupport {
    private static final String FORMULA_ID = "formula_1106285_v1";
    private static final String RULE_SET_ID = "ruleset_us_falcpa_demo_v1";

    @Autowired
    private FormulaCompositionPort formulaCompositionPort;

    @Autowired
    private AllergenFactsPort allergenFactsPort;

    @Test
    void derivesSeededFormulaAllergensThroughBothApplicationPorts() {
        var formula = formulaCompositionPort.findById(FORMULA_ID).orElseThrow();
        var result = allergenFactsPort.derive(formula, RULE_SET_ID, "US");

        assertThat(formula.isCurrentReleased()).isTrue();
        assertThat(formula.items()).hasSize(3);
        assertThat(result.facts()).extracting(fact -> fact.allergenCode())
                .containsExactly("SOY", "WHEAT");
        assertThat(result.facts()).flatExtracting(fact -> fact.derivationEvidence())
                .extracting(evidence -> evidence.ingredientAllergenId())
                .containsExactly("ia_soy_ruleset_us_falcpa_demo_v1", "ia_wheat_ruleset_us_falcpa_demo_v1");
        assertThat(result.unresolvedComponents()).isEmpty();
    }

    @Test
    void returnsTheSeededUsCatalogueInStableCodeOrder() {
        assertThat(allergenFactsPort.listAllergens("US"))
                .extracting(entry -> entry.allergenCode())
                .containsExactly("CRUSTACEAN_SHELLFISH", "EGG", "FISH", "MILK", "PEANUT",
                        "SESAME", "SOY", "TREE_NUTS", "WHEAT");
    }
}
