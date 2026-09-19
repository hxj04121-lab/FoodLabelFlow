package com.spectrace.support.fixture;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.application.contract.DeclaredAllergen.DeclarationType;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic positive truth data for SCRUM-13/SCRUM-23.
 *
 * <p>The records are test builders, not persistence entities. The companion
 * {@code s2-m2-positive-golden-fixtures.sql} resource supplies the same IDs to
 * MySQL tests without a Flyway migration or a dependency on the V3 baseline seed.</p>
 */
public final class PositiveGoldenFixtures {

    public static final String SQL_RESOURCE = "/fixtures/s2-m2-positive-golden-fixtures.sql";
    public static final String RULE_SET_VERSION_ID = "ruleset_s2_m2_positive_v1";
    public static final String JURISDICTION_CODE = "US";

    public static final String SOY_ID = "S2M2-POS-SOY-001";
    public static final String MILK_ID = "S2M2-POS-MILK-001";
    public static final String WHEAT_ID = "S2M2-POS-WHEAT-001";
    public static final String MULTI_ID = "S2M2-POS-MULTI-001";

    public static final List<Fixture> ALL = List.of(soy(), milk(), wheat(), multiItem());

    private PositiveGoldenFixtures() {
    }

    public static Fixture byId(String fixtureId) {
        return ALL.stream()
                .filter(fixture -> fixture.fixtureId().equals(fixtureId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown positive fixture: " + fixtureId));
    }

    private static Fixture soy() {
        SourceComponent component = component(
                "component_s2_m2_soy_v1", "ing_s2_m2_soy_lecithin",
                "Soy lecithin", "exact canonical fixture match");
        FormulaItem item = item(
                "item_s2_m2_soy_v1", "mat_s2_m2_soy", "spec_s2_m2_soy_v1", component);
        Declaration declaration = declaration("all_soy", "Contains: Soy");
        ExpectedFact fact = fact(
                "all_soy", "SOY", "ia_s2_m2_soy_v1", item, component,
                "fixture mapping: soy lecithin -> SOY");
        return fixture(
                SOY_ID, "prod_s2_m2_soy", "formula_s2_m2_soy_v1", "label_s2_m2_soy_v1",
                "Sunflower oil, soy lecithin.", "Contains: Soy",
                List.of(item), List.of(declaration), List.of(fact),
                List.of(finding("rule_s2_m2_soy_decl", "SOY_DECLARATION_PRESENT",
                        "SOY is derived and declared as CONTAINS.")));
    }

    private static Fixture milk() {
        SourceComponent component = component(
                "component_s2_m2_milk_v1", "ing_s2_m2_milk_powder",
                "Whole milk powder", "exact canonical fixture match");
        FormulaItem item = item(
                "item_s2_m2_milk_v1", "mat_s2_m2_milk", "spec_s2_m2_milk_v1", component);
        Declaration declaration = declaration("all_milk", "Contains: Milk");
        ExpectedFact fact = fact(
                "all_milk", "MILK", "ia_s2_m2_milk_v1", item, component,
                "fixture mapping: whole milk powder -> MILK");
        return fixture(
                MILK_ID, "prod_s2_m2_milk", "formula_s2_m2_milk_v1", "label_s2_m2_milk_v1",
                "Cocoa, whole milk powder.", "Contains: Milk",
                List.of(item), List.of(declaration), List.of(fact),
                List.of(finding("rule_s2_m2_milk_decl", "MILK_DECLARATION_PRESENT",
                        "MILK is derived and declared as CONTAINS.")));
    }

    private static Fixture wheat() {
        SourceComponent component = component(
                "component_s2_m2_wheat_v1", "ing_s2_m2_wheat_flour",
                "Enriched wheat flour", "exact canonical fixture match");
        FormulaItem item = item(
                "item_s2_m2_wheat_v1", "mat_s2_m2_wheat", "spec_s2_m2_wheat_v1", component);
        Declaration declaration = declaration("all_wheat", "Contains: Wheat");
        ExpectedFact fact = fact(
                "all_wheat", "WHEAT", "ia_s2_m2_wheat_v1", item, component,
                "fixture mapping: enriched wheat flour -> WHEAT");
        return fixture(
                WHEAT_ID, "prod_s2_m2_wheat", "formula_s2_m2_wheat_v1", "label_s2_m2_wheat_v1",
                "Enriched wheat flour, sea salt.", "Contains: Wheat",
                List.of(item), List.of(declaration), List.of(fact),
                List.of(finding("rule_s2_m2_wheat_decl", "WHEAT_DECLARATION_PRESENT",
                        "WHEAT is derived and declared as CONTAINS.")));
    }

    private static Fixture multiItem() {
        SourceComponent milkComponent = component(
                "component_s2_m2_milk_v1", "ing_s2_m2_milk_powder",
                "Whole milk powder", "exact canonical fixture match");
        SourceComponent soyComponent = component(
                "component_s2_m2_soy_v1", "ing_s2_m2_soy_lecithin",
                "Soy lecithin", "exact canonical fixture match");
        SourceComponent wheatComponent = component(
                "component_s2_m2_wheat_v1", "ing_s2_m2_wheat_flour",
                "Enriched wheat flour", "exact canonical fixture match");
        FormulaItem wheatItem = item(
                "item_s2_m2_multi_01", "mat_s2_m2_wheat", "spec_s2_m2_wheat_v1", wheatComponent);
        FormulaItem milkItem = item(
                "item_s2_m2_multi_02", "mat_s2_m2_milk", "spec_s2_m2_milk_v1", milkComponent);
        FormulaItem soyItem = item(
                "item_s2_m2_multi_03", "mat_s2_m2_soy", "spec_s2_m2_soy_v1", soyComponent);
        String labelDeclaration = "Contains: Milk, Soy, Wheat";
        return fixture(
                MULTI_ID, "prod_s2_m2_multi", "formula_s2_m2_multi_v1", "label_s2_m2_multi_v1",
                "Enriched wheat flour, whole milk powder, soy lecithin.", labelDeclaration,
                List.of(wheatItem, milkItem, soyItem),
                List.of(
                        declaration("all_milk", labelDeclaration),
                        declaration("all_soy", labelDeclaration),
                        declaration("all_wheat", labelDeclaration)),
                List.of(
                        fact("all_milk", "MILK", "ia_s2_m2_milk_v1", milkItem, milkComponent,
                                "fixture mapping: whole milk powder -> MILK"),
                        fact("all_soy", "SOY", "ia_s2_m2_soy_v1", soyItem, soyComponent,
                                "fixture mapping: soy lecithin -> SOY"),
                        fact("all_wheat", "WHEAT", "ia_s2_m2_wheat_v1", wheatItem, wheatComponent,
                                "fixture mapping: enriched wheat flour -> WHEAT")),
                List.of(
                        finding("rule_s2_m2_milk_decl", "MILK_DECLARATION_PRESENT",
                                "MILK is derived and declared as CONTAINS."),
                        finding("rule_s2_m2_soy_decl", "SOY_DECLARATION_PRESENT",
                                "SOY is derived and declared as CONTAINS."),
                        finding("rule_s2_m2_wheat_decl", "WHEAT_DECLARATION_PRESENT",
                                "WHEAT is derived and declared as CONTAINS.")));
    }

    private static Fixture fixture(
            String fixtureId,
            String productId,
            String formulaVersionId,
            String labelVersionId,
            String rawIngredientText,
            String expectedLabelDeclaration,
            List<FormulaItem> formulaItems,
            List<Declaration> declarations,
            List<ExpectedFact> expectedFacts,
            List<ExpectedFinding> expectedFindings
    ) {
        return new Fixture(
                fixtureId, productId, formulaVersionId, labelVersionId,
                RULE_SET_VERSION_ID, JURISDICTION_CODE, rawIngredientText, true,
                expectedLabelDeclaration, formulaItems, declarations, expectedFacts,
                expectedFindings, ValidationStatus.PASSED);
    }

    private static FormulaItem item(
            String formulaItemId,
            String supplierMaterialId,
            String specificationVersionId,
            SourceComponent component
    ) {
        return new FormulaItem(formulaItemId, supplierMaterialId, specificationVersionId, List.of(component));
    }

    private static SourceComponent component(
            String specComponentId,
            String ingredientId,
            String rawPhrase,
            String matchRule
    ) {
        return new SourceComponent(
                specComponentId, ingredientId, rawPhrase, matchRule,
                FormulaCompositionSnapshot.MatchStatus.MATCHED);
    }

    private static Declaration declaration(String allergenId, String displayText) {
        return new Declaration(
                allergenId, DeclarationType.CONTAINS, "FORMULA_DERIVED", displayText);
    }

    private static ExpectedFact fact(
            String allergenId,
            String allergenCode,
            String ingredientAllergenId,
            FormulaItem item,
            SourceComponent component,
            String evidenceRule
    ) {
        return new ExpectedFact(
                allergenId, allergenCode, DeclarationType.CONTAINS,
                List.of(new Evidence(
                        item.formulaItemId(), item.specificationVersionId(),
                        component.specComponentId(), component.ingredientId(),
                        ingredientAllergenId, evidenceRule, "prov_s2_m2_positive_v1")));
    }

    private static ExpectedFinding finding(String ruleDefinitionId, String resultCode, String message) {
        return new ExpectedFinding(
                ruleDefinitionId, resultCode, ValidationSeverity.INFO, true, false, message);
    }

    public record Fixture(
            String fixtureId,
            String productId,
            String formulaVersionId,
            String labelVersionId,
            String ruleSetVersionId,
            String jurisdictionCode,
            String rawIngredientText,
            boolean current,
            String expectedLabelDeclaration,
            List<FormulaItem> formulaItems,
            List<Declaration> declarations,
            List<ExpectedFact> expectedFacts,
            List<ExpectedFinding> expectedFindings,
            ValidationStatus expectedResult
    ) {
        public Fixture {
            fixtureId = required(fixtureId, "fixtureId");
            productId = required(productId, "productId");
            formulaVersionId = required(formulaVersionId, "formulaVersionId");
            labelVersionId = required(labelVersionId, "labelVersionId");
            ruleSetVersionId = required(ruleSetVersionId, "ruleSetVersionId");
            jurisdictionCode = required(jurisdictionCode, "jurisdictionCode");
            rawIngredientText = required(rawIngredientText, "rawIngredientText");
            expectedLabelDeclaration = required(expectedLabelDeclaration, "expectedLabelDeclaration");
            formulaItems = copy(formulaItems, "formulaItems");
            declarations = copy(declarations, "declarations");
            expectedFacts = copy(expectedFacts, "expectedFacts");
            expectedFindings = copy(expectedFindings, "expectedFindings");
            expectedResult = Objects.requireNonNull(expectedResult, "expectedResult");
            if (!current || expectedResult != ValidationStatus.PASSED
                    || expectedFindings.stream().anyMatch(finding -> !finding.passed() || finding.blocking())) {
                throw new IllegalArgumentException("A positive fixture must be current and non-blocking PASSED truth");
            }
            if (!factsHaveMatchingDeclarations(expectedFacts, declarations)) {
                throw new IllegalArgumentException("Every expected fact requires an exact matching declaration type");
            }
        }

        public FormulaCompositionSnapshot formulaSnapshot() {
            return new FormulaCompositionSnapshot(
                    productId, formulaVersionId, true,
                    formulaItems.stream().map(FormulaItem::toContract).toList());
        }

        public LabelValidationSnapshot labelSnapshot() {
            return new LabelValidationSnapshot(
                    labelVersionId, productId, formulaVersionId, ruleSetVersionId,
                    jurisdictionCode, rawIngredientText, current,
                    "prov_s2_m2_positive_v1",
                    declarations.stream().map(Declaration::toContract).toList());
        }

        public AllergenDerivation expectedDerivation() {
            return new AllergenDerivation(
                    formulaVersionId, ruleSetVersionId, jurisdictionCode,
                    expectedFacts.stream().map(ExpectedFact::toContract).toList(), List.of());
        }

        public List<ValidationFinding> findings() {
            return expectedFindings.stream().map(ExpectedFinding::toContract).toList();
        }
    }

    public record FormulaItem(
            String formulaItemId,
            String supplierMaterialId,
            String specificationVersionId,
            List<SourceComponent> components
    ) {
        public FormulaItem {
            formulaItemId = required(formulaItemId, "formulaItemId");
            supplierMaterialId = required(supplierMaterialId, "supplierMaterialId");
            specificationVersionId = required(specificationVersionId, "specificationVersionId");
            components = copy(components, "components");
        }

        FormulaCompositionSnapshot.Item toContract() {
            return new FormulaCompositionSnapshot.Item(
                    formulaItemId, supplierMaterialId, specificationVersionId,
                    components.stream().map(SourceComponent::toContract).toList());
        }
    }

    public record SourceComponent(
            String specComponentId,
            String ingredientId,
            String rawPhrase,
            String matchRule,
            FormulaCompositionSnapshot.MatchStatus matchStatus
    ) {
        public SourceComponent {
            specComponentId = required(specComponentId, "specComponentId");
            ingredientId = required(ingredientId, "ingredientId");
            rawPhrase = required(rawPhrase, "rawPhrase");
            matchRule = required(matchRule, "matchRule");
            matchStatus = Objects.requireNonNull(matchStatus, "matchStatus");
        }

        FormulaCompositionSnapshot.Component toContract() {
            return new FormulaCompositionSnapshot.Component(
                    specComponentId, ingredientId, rawPhrase, matchRule, matchStatus);
        }
    }

    public record Declaration(
            String allergenId,
            DeclarationType declarationType,
            String declarationSource,
            String displayText
    ) {
        public Declaration {
            allergenId = required(allergenId, "allergenId");
            declarationType = Objects.requireNonNull(declarationType, "declarationType");
            declarationSource = required(declarationSource, "declarationSource");
            displayText = required(displayText, "displayText");
        }

        LabelValidationSnapshot.AllergenDeclaration toContract() {
            return new LabelValidationSnapshot.AllergenDeclaration(
                    allergenId, declarationType.name(), declarationSource, displayText);
        }
    }

    public record ExpectedFact(
            String allergenId,
            String allergenCode,
            DeclarationType presence,
            List<Evidence> evidence
    ) {
        public ExpectedFact {
            allergenId = required(allergenId, "allergenId");
            allergenCode = required(allergenCode, "allergenCode");
            presence = Objects.requireNonNull(presence, "presence");
            evidence = copy(evidence, "evidence");
        }

        AllergenFact toContract() {
            return new AllergenFact(
                    allergenId, allergenCode, evidence.stream().map(Evidence::toContract).toList());
        }
    }

    public record Evidence(
            String formulaItemId,
            String specificationVersionId,
            String specComponentId,
            String ingredientId,
            String ingredientAllergenId,
            String evidenceRule,
            String dataProvenanceId
    ) {
        public Evidence {
            formulaItemId = required(formulaItemId, "formulaItemId");
            specificationVersionId = required(specificationVersionId, "specificationVersionId");
            specComponentId = required(specComponentId, "specComponentId");
            ingredientId = required(ingredientId, "ingredientId");
            ingredientAllergenId = required(ingredientAllergenId, "ingredientAllergenId");
            evidenceRule = required(evidenceRule, "evidenceRule");
            dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
        }

        AllergenFact.DerivationEvidence toContract() {
            return new AllergenFact.DerivationEvidence(
                    formulaItemId, specificationVersionId, specComponentId, ingredientId,
                    ingredientAllergenId, evidenceRule, dataProvenanceId);
        }
    }

    public record ExpectedFinding(
            String ruleDefinitionId,
            String resultCode,
            ValidationSeverity severity,
            boolean passed,
            boolean blocking,
            String message
    ) {
        public ExpectedFinding {
            ruleDefinitionId = required(ruleDefinitionId, "ruleDefinitionId");
            resultCode = required(resultCode, "resultCode");
            severity = Objects.requireNonNull(severity, "severity");
            message = required(message, "message");
        }

        ValidationFinding toContract() {
            return new ValidationFinding(
                    ruleDefinitionId, resultCode, severity, passed, blocking, message);
        }
    }

    private static <T> List<T> copy(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must contain non-null fixture values");
        }
        return List.copyOf(values);
    }

    private static boolean factsHaveMatchingDeclarations(
            List<ExpectedFact> facts,
            List<Declaration> declarations
    ) {
        return facts.stream().allMatch(fact -> declarations.stream().anyMatch(declaration ->
                declaration.allergenId().equals(fact.allergenId())
                        && declaration.declarationType() == fact.presence()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
