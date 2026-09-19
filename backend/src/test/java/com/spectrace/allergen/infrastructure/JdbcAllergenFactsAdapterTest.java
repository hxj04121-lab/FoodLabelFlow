package com.spectrace.allergen.infrastructure;

import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class JdbcAllergenFactsAdapterTest {
    @Test
    void derivesSortedFactsRetainsEveryEvidencePathAndKeepsUnresolvedComponents() {
        var jdbc = new StubJdbcTemplate(List.of(
                row("ingredient_id", "ing_soy", "ingredient_allergen_id", "ia_soy", "allergen_id", "all_soy",
                        "allergen_code", "SOY", "evidence_rule", "soy mapping", "data_provenance_id", "prov-soy"),
                row("ingredient_id", "ing_milk", "ingredient_allergen_id", "ia_milk", "allergen_id", "all_milk",
                        "allergen_code", "MILK", "evidence_rule", "milk mapping", "data_provenance_id", "prov-milk")),
                List.of());
        var adapter = new JdbcAllergenFactsAdapter(jdbc);

        var result = adapter.derive(new FormulaCompositionSnapshot(
                        "product-1", "formula-1", true, List.of(
                                new Item("item-1", "material-1", "spec-1", List.of(
                                        component("component-soy-1", "ing_soy", MatchStatus.MATCHED),
                                        component("component-unmapped", "ing-unknown", MatchStatus.UNMAPPED))),
                                new Item("item-2", "material-2", "spec-2", List.of(
                                        component("component-soy-2", "ing_soy", MatchStatus.MATCHED),
                                        component("component-ambiguous", "ing-ambiguous", MatchStatus.AMBIGUOUS))),
                                new Item("item-3", "material-3", "spec-3", List.of(
                                        component("component-milk", "ing_milk", MatchStatus.MATCHED),
                                        component("component-neutral", "ing-neutral", MatchStatus.MATCHED))))),
                "rules-1", "US");

        assertThat(result.facts()).extracting(AllergenFact::allergenCode)
                .containsExactly("MILK", "SOY");
        assertThat(result.facts().get(1).derivationEvidence())
                .extracting(AllergenFact.DerivationEvidence::formulaItemId)
                .containsExactly("item-1", "item-2");
        assertThat(result.facts().get(1).derivationEvidence().getFirst().dataProvenanceId())
                .isEqualTo("prov-soy");
        assertThat(result.unresolvedComponents())
                .extracting(unresolved -> unresolved.specComponentId())
                .containsExactly("component-unmapped", "component-ambiguous");
        assertThat(result.unresolvedComponents())
                .extracting(unresolved -> unresolved.matchStatus())
                .containsExactly(MatchStatus.UNMAPPED, MatchStatus.AMBIGUOUS);
        assertThat(jdbc.lastSql).contains("ingredient_allergen", "ORDER BY ia.ingredient_id");
        assertThat(jdbc.lastArguments).contains("rules-1", "US", "ing_soy", "ing_milk", "ing-neutral");
    }

    @Test
    void anMatchedIngredientWithoutAConfiguredMappingIsACompletedNegativePath() {
        var jdbc = new StubJdbcTemplate(List.of(), List.of());
        var adapter = new JdbcAllergenFactsAdapter(jdbc);

        var result = adapter.derive(new FormulaCompositionSnapshot(
                        "product-1", "formula-1", true,
                        List.of(new Item("item-1", "material-1", "spec-1", List.of(
                                component("component-neutral", "ing-neutral", MatchStatus.MATCHED))))),
                "rules-1", "US");

        assertThat(result.facts()).isEmpty();
        assertThat(result.unresolvedComponents()).isEmpty();
    }

    @Test
    void listsOnlyTheRequestedJurisdictionAndUsesStableCatalogueOrder() {
        var jdbc = new StubJdbcTemplate(List.of(), List.of(
                row("allergen_id", "all_milk", "allergen_code", "MILK", "display_name", "Milk", "jurisdiction_code", "US"),
                row("allergen_id", "all_soy", "allergen_code", "SOY", "display_name", "Soy", "jurisdiction_code", "US")));
        var adapter = new JdbcAllergenFactsAdapter(jdbc);

        assertThat(adapter.listAllergens("US"))
                .extracting(AllergenEntry::allergenCode)
                .containsExactly("MILK", "SOY");
        assertThat(jdbc.lastSql).contains("WHERE jurisdiction_code = ?", "ORDER BY allergen_code, allergen_id");
        assertThat(jdbc.lastArguments).containsExactly("US");
    }

    @Test
    void rejectsMissingBoundaryInputsWithoutQueryingPersistence() {
        var jdbc = new StubJdbcTemplate(List.of(), List.of());
        var adapter = new JdbcAllergenFactsAdapter(jdbc);
        assertThatNullPointerException().isThrownBy(() -> adapter.derive(null, "rules-1", "US"));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.derive(
                new FormulaCompositionSnapshot("product-1", "formula-1", true, List.of()), " ", "US"));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.listAllergens(" "));
        assertThat(jdbc.lastSql).isNull();
    }

    private static Component component(String id, String ingredientId, MatchStatus status) {
        return new Component(id, ingredientId, id + " phrase", "fixture match", status);
    }

    private static Map<String, Object> row(Object... values) {
        var row = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> mappingRows;
        private final List<Map<String, Object>> allergenRows;
        private String lastSql;
        private List<Object> lastArguments;

        private StubJdbcTemplate(
                List<Map<String, Object>> mappingRows, List<Map<String, Object>> allergenRows) {
            this.mappingRows = mappingRows;
            this.allergenRows = allergenRows;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastSql = sql;
            lastArguments = new ArrayList<>(List.of(args));
            if (sql.contains("FROM allergen")) return allergenRows;
            if (sql.contains("FROM ingredient_allergen")) return mappingRows;
            throw new AssertionError("Unexpected SQL: " + sql);
        }
    }
}
