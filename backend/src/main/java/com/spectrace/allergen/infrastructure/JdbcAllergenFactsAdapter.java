package com.spectrace.allergen.infrastructure;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Allergen-owned adapter; formula/specification data enters only through the catalog snapshot. */
@Repository
public class JdbcAllergenFactsAdapter implements AllergenFactsPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAllergenFactsAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AllergenEntry> listAllergens(String jurisdictionCode) {
        return jdbcTemplate.query(
                """
                SELECT allergen_id, allergen_code, display_name, jurisdiction_code
                FROM allergen
                WHERE jurisdiction_code = ?
                ORDER BY allergen_code, allergen_id
                """,
                (rs, rowNum) -> new AllergenEntry(
                        rs.getString("allergen_id"),
                        rs.getString("allergen_code"),
                        rs.getString("display_name"),
                        rs.getString("jurisdiction_code")
                ),
                jurisdictionCode
        );
    }

    @Override
    public AllergenDerivation derive(
            FormulaCompositionSnapshot formula,
            String ruleSetVersionId,
            String jurisdictionCode
    ) {
        Objects.requireNonNull(formula, "formula");
        List<AllergenDerivation.UnresolvedComponent> unresolved = new ArrayList<>();
        List<ComponentRef> matched = new ArrayList<>();
        for (Item item : formula.items()) {
            for (Component component : item.components()) {
                if (component.matchStatus() == FormulaCompositionSnapshot.MatchStatus.MATCHED) {
                    matched.add(new ComponentRef(item, component));
                } else {
                    unresolved.add(new AllergenDerivation.UnresolvedComponent(
                            item.formulaItemId(), item.specificationVersionId(),
                            component.specComponentId(), component.ingredientId(),
                            component.rawPhrase(), component.matchRule(), component.matchStatus()));
                }
            }
        }

        Map<String, List<MappingRow>> mappings = loadMappings(matched, ruleSetVersionId, jurisdictionCode);
        Map<String, FactBuilder> facts = new LinkedHashMap<>();
        for (ComponentRef ref : matched) {
            for (MappingRow mapping : mappings.getOrDefault(ref.component().ingredientId(), List.of())) {
                facts.computeIfAbsent(mapping.allergenId(), ignored ->
                        new FactBuilder(mapping.allergenId(), mapping.allergenCode()))
                        .evidence.add(new AllergenFact.DerivationEvidence(
                                ref.item().formulaItemId(), ref.item().specificationVersionId(),
                                ref.component().specComponentId(), ref.component().ingredientId(),
                                mapping.ingredientAllergenId(), mapping.evidenceRule(),
                                mapping.dataProvenanceId()));
            }
        }

        List<AllergenFact> derived = facts.values().stream()
                .sorted(Comparator.comparing(FactBuilder::allergenCode)
                        .thenComparing(FactBuilder::allergenId))
                .map(builder -> new AllergenFact(
                        builder.allergenId(), builder.allergenCode(), builder.evidence()))
                .toList();
        return new AllergenDerivation(
                formula.formulaVersionId(), ruleSetVersionId, jurisdictionCode, derived, unresolved);
    }

    private Map<String, List<MappingRow>> loadMappings(
            List<ComponentRef> matched,
            String ruleSetVersionId,
            String jurisdictionCode
    ) {
        List<String> ingredientIds = matched.stream()
                .map(ref -> ref.component().ingredientId())
                .distinct()
                .toList();
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", ingredientIds.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>(ingredientIds);
        args.add(ruleSetVersionId);
        args.add(jurisdictionCode);
        String sql = """
                SELECT ia.ingredient_id, ia.ingredient_allergen_id, ia.allergen_id,
                       a.allergen_code, ia.evidence_rule, ia.data_provenance_id
                FROM ingredient_allergen ia
                JOIN allergen a ON a.allergen_id = ia.allergen_id
                WHERE ia.ingredient_id IN (""" + placeholders + """
                )
                  AND ia.rule_set_version_id = ?
                  AND a.jurisdiction_code = ?
                ORDER BY a.allergen_code, a.allergen_id, ia.ingredient_id,
                         ia.ingredient_allergen_id
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MappingRow(
                        rs.getString("ingredient_id"),
                        rs.getString("ingredient_allergen_id"),
                        rs.getString("allergen_id"),
                        rs.getString("allergen_code"),
                        rs.getString("evidence_rule"),
                        rs.getString("data_provenance_id")
                ),
                args.toArray()
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                MappingRow::ingredientId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    private record ComponentRef(Item item, Component component) {
    }

    private record MappingRow(
            String ingredientId,
            String ingredientAllergenId,
            String allergenId,
            String allergenCode,
            String evidenceRule,
            String dataProvenanceId
    ) {
    }

    private static final class FactBuilder {
        private final String allergenId;
        private final String allergenCode;
        private final List<AllergenFact.DerivationEvidence> evidence = new ArrayList<>();

        private FactBuilder(String allergenId, String allergenCode) {
            this.allergenId = allergenId;
            this.allergenCode = allergenCode;
        }

        String allergenId() { return allergenId; }
        String allergenCode() { return allergenCode; }
        List<AllergenFact.DerivationEvidence> evidence() { return evidence; }
    }
}
