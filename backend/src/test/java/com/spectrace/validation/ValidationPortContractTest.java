package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.label.application.port.LabelValidationSnapshot.AllergenDeclaration;
import com.spectrace.validation.application.rule.RuleEvaluationContext;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationPortContractTest {
    @Test
    void missingAndExistingNonCurrentLabelsRemainDistinctAndDraftDeclarationsCanBeEmpty() {
        var stale = label(false, List.of());
        LabelSnapshotPort port = id -> "label-1".equals(id) ? Optional.of(stale) : Optional.empty();

        assertThat(port.findById("missing")).isEmpty();
        assertThat(port.findById("label-1")).contains(stale);
        assertThat(stale.isCurrent()).isFalse();
        assertThat(label(true, List.of()).isCurrent()).isTrue();
        assertThat(label(true, List.of()).declarations()).isEmpty();
    }

    @Test
    void declarationsAreCopiedAndCannotChangeAnAlreadyCapturedSnapshot() {
        var declarations = new ArrayList<>(List.of(
                new AllergenDeclaration("all_soy", "CONTAINS", "USER_ENTERED", null)));
        var snapshot = label(true, declarations);
        declarations.clear();

        assertThat(snapshot.declarations()).hasSize(1);
        assertThat(snapshot.declarations().getFirst().displayText()).isNull();
        assertThatThrownBy(() -> snapshot.declarations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> label(true, null));
    }

    @Test
    void formulaPreservesAllItemsPinnedSpecificationsAndUnresolvedComponentsImmutably() {
        var components = new ArrayList<>(List.of(component("component-1", MatchStatus.MATCHED),
                component("component-2", MatchStatus.UNMAPPED)));
        var items = new ArrayList<>(List.of(new Item("item-1", "material-1", "spec-1", components),
                new Item("item-2", "material-2", "spec-2",
                        List.of(component("component-3", MatchStatus.AMBIGUOUS)))));
        var formula = new FormulaCompositionSnapshot("product-1", "formula-1", true, items);
        components.clear();
        items.clear();

        assertThat(formula.items()).extracting(Item::specificationVersionId)
                .containsExactly("spec-1", "spec-2");
        assertThat(formula.items().getFirst().components()).extracting(Component::matchStatus)
                .containsExactly(MatchStatus.MATCHED, MatchStatus.UNMAPPED);
        assertThat(formula.items().getLast().components().getFirst().matchStatus())
                .isEqualTo(MatchStatus.AMBIGUOUS);
        assertThatThrownBy(() -> formula.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> formula.items().getFirst().components().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allergenEvidenceRetainsContributionsFromTwoFormulaItemsAndCannotBeInvented() {
        var evidence = new ArrayList<>(List.of(evidence("item-1", "spec-1"), evidence("item-2", "spec-2")));
        var soy = new AllergenFact("all_soy", "SOY", evidence);
        var facts = new ArrayList<>(List.of(soy));
        var derivation = new AllergenDerivation("formula-1", "rules-1", "US", facts, List.of());
        evidence.clear();
        facts.clear();

        assertThat(derivation.facts()).containsExactly(soy);
        assertThat(soy.derivationEvidence()).extracting(AllergenFact.DerivationEvidence::formulaItemId)
                .containsExactly("item-1", "item-2");
        assertThatThrownBy(() -> soy.derivationEvidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> derivation.facts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new AllergenFact("all_soy", "SOY", List.of()));
    }

    @Test
    void completedNegativeDerivationIsDifferentFromMissingOrUnresolvedData() {
        var unresolved = new ArrayList<>(List.of(new AllergenDerivation.UnresolvedComponent(
                "item-1", "spec-1", "component-1", "ingredient-1", "unknown", "no unique match", MatchStatus.UNMAPPED)));
        var incomplete = new AllergenDerivation("formula-1", "rules-1", "US", List.of(), unresolved);
        unresolved.clear();

        assertThat(incomplete.facts()).isEmpty();
        assertThat(incomplete.unresolvedComponents()).hasSize(1);
        assertThat(incomplete.unresolvedComponents().getFirst().ingredientId()).isEqualTo("ingredient-1");
        assertThat(incomplete.unresolvedComponents().getFirst().matchRule()).isEqualTo("no unique match");
        assertThat(derivation("formula-1", "rules-1", "US").unresolvedComponents()).isEmpty();
        assertThatThrownBy(() -> incomplete.unresolvedComponents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() ->
                new AllergenDerivation("formula-1", "rules-1", "US", null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AllergenDerivation.UnresolvedComponent(
                "item-1", "spec-1", "component-1", "ingredient-1", "known", "exact match", MatchStatus.MATCHED));
    }

    @Test
    void evaluationContextRejectsMixedFormulaRuleSetAndJurisdictionInputs() {
        var label = label(true, List.of());
        var rules = rules("rules-1", "US");
        assertThat(new RuleEvaluationContext(label, rules, derivation("formula-1", "rules-1", "US"))
                .label()).isSameAs(label);

        for (var mismatched : List.of(derivation("formula-2", "rules-1", "US"),
                derivation("formula-1", "rules-2", "US"), derivation("formula-1", "rules-1", "SG"))) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RuleEvaluationContext(label, rules, mismatched));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new RuleEvaluationContext(label,
                rules("rules-2", "US"), derivation("formula-1", "rules-2", "US")));
        assertThatIllegalArgumentException().isThrownBy(() -> new RuleEvaluationContext(label,
                rules("rules-1", "SG"), derivation("formula-1", "rules-1", "SG")));
    }

    @Test
    void contractIdentifiersAreRequiredAndAreNotSilentlyNormalized() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new FormulaCompositionSnapshot(" ", "formula-1", true, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new AllergenDeclaration(null, "CONTAINS", "USER_ENTERED", null));
        assertThat(new FormulaCompositionSnapshot(" product-1 ", "formula-1", true, List.of()).productId())
                .isEqualTo(" product-1 ");
    }

    private LabelValidationSnapshot label(boolean current, List<AllergenDeclaration> declarations) {
        return new LabelValidationSnapshot("label-1", "product-1", "formula-1", "rules-1", "US",
                "", current, "prov-test", declarations);
    }

    private Component component(String id, MatchStatus status) {
        return new Component(id, "ingredient-1", "raw phrase", "test match evidence", status);
    }

    private AllergenFact.DerivationEvidence evidence(String itemId, String specId) {
        return new AllergenFact.DerivationEvidence(itemId, specId, "component-" + itemId,
                "ingredient-1", "mapping-1", "test mapping evidence", "prov-test");
    }

    private AllergenDerivation derivation(String formulaId, String rulesId, String jurisdiction) {
        return new AllergenDerivation(formulaId, rulesId, jurisdiction, List.of(), List.of());
    }

    private RuleSetVersion rules(String id, String jurisdiction) {
        return new RuleSetVersion(id, "TEST_RULES", "1", jurisdiction, RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 9, 1), null, true, "Test contract", "prov-test", List.of());
    }
}
