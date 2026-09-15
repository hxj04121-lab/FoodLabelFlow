package com.spectrace.allergen.infrastructure;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Reads allergen-owned mappings and derives facts from an already captured formula snapshot. */
@Repository
public class JdbcAllergenFactsAdapter implements AllergenFactsPort {
    private final JdbcTemplate jdbc;

    public JdbcAllergenFactsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AllergenEntry> listAllergens(String jurisdictionCode) {
        requiredText(jurisdictionCode, "jurisdictionCode");
        return jdbc.queryForList("""
                        SELECT allergen_id, allergen_code, display_name, jurisdiction_code
                        FROM allergen
                        WHERE jurisdiction_code = ?
                        ORDER BY allergen_code, allergen_id
                        """, jurisdictionCode).stream()
                .map(row -> new AllergenEntry(
                        (String) row.get("allergen_id"),
                        (String) row.get("allergen_code"),
                        (String) row.get("display_name"),
                        (String) row.get("jurisdiction_code")))
                .toList();
    }

    @Override
    public AllergenDerivation derive(
            FormulaCompositionSnapshot formula, String ruleSetVersionId, String jurisdictionCode) {
        if (formula == null) throw new NullPointerException("formula");
        requiredText(ruleSetVersionId, "ruleSetVersionId");
        requiredText(jurisdictionCode, "jurisdictionCode");

        List<String> matchedIngredientIds = formula.items().stream()
                .flatMap(item -> item.components().stream())
                .filter(component -> component.matchStatus() == MatchStatus.MATCHED)
                .map(Component::ingredientId)
                .distinct()
                .toList();
        Map<String, List<IngredientAllergenMapping>> mappingsByIngredient =
                mappingsByIngredient(matchedIngredientIds, ruleSetVersionId, jurisdictionCode);

        Map<AllergenKey, List<AllergenFact.DerivationEvidence>> evidenceByAllergen = new HashMap<>();
        List<AllergenDerivation.UnresolvedComponent> unresolved = new ArrayList<>();
        for (var item : formula.items()) {
            for (var component : item.components()) {
                if (component.matchStatus() != MatchStatus.MATCHED) {
                    unresolved.add(new AllergenDerivation.UnresolvedComponent(
                            item.formulaItemId(), item.specificationVersionId(), component.specComponentId(),
                            component.ingredientId(), component.rawPhrase(), component.matchRule(),
                            component.matchStatus()));
                    continue;
                }
                for (var mapping : mappingsByIngredient.getOrDefault(component.ingredientId(), List.of())) {
                    var evidence = new AllergenFact.DerivationEvidence(
                            item.formulaItemId(), item.specificationVersionId(), component.specComponentId(),
                            component.ingredientId(), mapping.ingredientAllergenId(), mapping.evidenceRule(),
                            mapping.dataProvenanceId());
                    evidenceByAllergen
                            .computeIfAbsent(new AllergenKey(mapping.allergenId(), mapping.allergenCode()), ignored -> new ArrayList<>())
                            .add(evidence);
                }
            }
        }

        List<AllergenFact> facts = evidenceByAllergen.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(AllergenKey::allergenCode)
                        .thenComparing(AllergenKey::allergenId)))
                .map(entry -> new AllergenFact(
                        entry.getKey().allergenId(), entry.getKey().allergenCode(), entry.getValue()))
                .toList();
        return new AllergenDerivation(
                formula.formulaVersionId(), ruleSetVersionId, jurisdictionCode, facts, unresolved);
    }

    private Map<String, List<IngredientAllergenMapping>> mappingsByIngredient(
            List<String> ingredientIds, String ruleSetVersionId, String jurisdictionCode) {
        if (ingredientIds.isEmpty()) return Map.of();

        String placeholders = String.join(",", java.util.Collections.nCopies(ingredientIds.size(), "?"));
        Object[] arguments = new Object[ingredientIds.size() + 2];
        arguments[0] = ruleSetVersionId;
        arguments[1] = jurisdictionCode;
        for (int i = 0; i < ingredientIds.size(); i++) arguments[i + 2] = ingredientIds.get(i);

        String sql = """
                SELECT ia.ingredient_id, ia.ingredient_allergen_id, ia.allergen_id,
                       a.allergen_code, ia.evidence_rule, ia.data_provenance_id
                FROM ingredient_allergen ia
                JOIN allergen a ON a.allergen_id = ia.allergen_id
                WHERE ia.rule_set_version_id = ?
                  AND a.jurisdiction_code = ?
                  AND ia.ingredient_id IN (%s)
                ORDER BY ia.ingredient_id, a.allergen_code, a.allergen_id, ia.ingredient_allergen_id
                """.formatted(placeholders);
        return jdbc.queryForList(sql, arguments).stream()
                .map(row -> new IngredientAllergenMapping(
                        (String) row.get("ingredient_id"),
                        (String) row.get("ingredient_allergen_id"),
                        (String) row.get("allergen_id"),
                        (String) row.get("allergen_code"),
                        (String) row.get("evidence_rule"),
                        (String) row.get("data_provenance_id")))
                .collect(java.util.stream.Collectors.groupingBy(
                        IngredientAllergenMapping::ingredientId, HashMap::new, java.util.stream.Collectors.toList()));
    }

    private record AllergenKey(String allergenId, String allergenCode) {
    }

    record IngredientAllergenMapping(
            String ingredientId,
            String ingredientAllergenId,
            String allergenId,
            String allergenCode,
            String evidenceRule,
            String dataProvenanceId
    ) {
    }
}
