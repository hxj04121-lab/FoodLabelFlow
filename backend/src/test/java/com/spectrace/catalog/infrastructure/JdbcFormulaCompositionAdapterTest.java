package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JdbcFormulaCompositionAdapterTest {
    @Test
    void capturesExactFormulaItemsAndNestedComponentsWithoutCrossingCatalogRows() {
        var jdbc = new StubJdbcTemplate(
                List.of(row("product_id", "product-1", "formula_version_id", "formula-1",
                        "is_current_released", "Y")),
                List.of(
                        row("formula_item_id", "item-2", "supplier_material_id", "material-2",
                                "specification_version_id", "spec-2"),
                        row("formula_item_id", "item-1", "supplier_material_id", "material-1",
                                "specification_version_id", "spec-1")),
                Map.of(
                        "spec-1", List.of(row("spec_component_id", "component-1", "ingredient_id", "ingredient-1",
                                "raw_phrase", "Soy lecithin", "match_rule", "exact canonical match",
                                "match_status", "MATCHED")),
                        "spec-2", List.of(row("spec_component_id", "component-2", "ingredient_id", "ingredient-2",
                                "raw_phrase", "Unknown phrase", "match_rule", "no unique match",
                                "match_status", "AMBIGUOUS"))));

        var snapshot = new JdbcFormulaCompositionAdapter(jdbc).findById("formula-1").orElseThrow();

        assertThat(snapshot.productId()).isEqualTo("product-1");
        assertThat(snapshot.formulaVersionId()).isEqualTo("formula-1");
        assertThat(snapshot.isCurrentReleased()).isTrue();
        assertThat(snapshot.items()).extracting(item -> item.formulaItemId())
                .containsExactly("item-2", "item-1");
        assertThat(snapshot.items().getFirst().components().getFirst().matchStatus())
                .isEqualTo(MatchStatus.AMBIGUOUS);
        assertThat(jdbc.queries).anySatisfy(sql -> assertThat(sql).contains("FOR UPDATE"));
    }

    @Test
    void absentFormulaIsDistinctFromAnExistingFormulaWithNoItems() {
        var jdbc = new StubJdbcTemplate(List.of(), List.of(), Map.of());

        assertThat(new JdbcFormulaCompositionAdapter(jdbc).findById("missing")).isEmpty();
        assertThat(jdbc.queries).hasSize(1);
    }

    @Test
    void rejectsBlankFormulaIdentifiersBeforeReadingStorage() {
        var jdbc = new StubJdbcTemplate(List.of(), List.of(), Map.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JdbcFormulaCompositionAdapter(jdbc).findById(" "));
        assertThat(jdbc.queries).isEmpty();
    }

    private static Map<String, Object> row(Object... values) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> formulaRows;
        private final List<Map<String, Object>> itemRows;
        private final Map<String, List<Map<String, Object>>> componentsBySpecification;
        private final List<String> queries = new ArrayList<>();

        private StubJdbcTemplate(
                List<Map<String, Object>> formulaRows,
                List<Map<String, Object>> itemRows,
                Map<String, List<Map<String, Object>>> componentsBySpecification) {
            this.formulaRows = formulaRows;
            this.itemRows = itemRows;
            this.componentsBySpecification = componentsBySpecification;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queries.add(sql);
            if (sql.contains("FROM formula_version")) return formulaRows;
            if (sql.contains("FROM formula_item")) return itemRows;
            if (sql.contains("FROM spec_component")) {
                return componentsBySpecification.getOrDefault((String) args[0], List.of());
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }
    }
}
