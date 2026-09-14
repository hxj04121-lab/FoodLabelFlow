package com.spectrace.validation.application;

import com.spectrace.validation.application.contract.AllergenFact;
import com.spectrace.validation.application.contract.DeclaredAllergen;
import com.spectrace.validation.application.contract.FormulaComponent;
import com.spectrace.validation.application.contract.FormulaItem;
import com.spectrace.validation.application.contract.LabelSnapshotLookup;
import com.spectrace.validation.application.contract.LabelValidationSnapshot;
import com.spectrace.validation.application.contract.ValidationFinding;
import com.spectrace.validation.application.port.AllergenFactsPort;
import com.spectrace.validation.application.port.AuthorizationPort;
import com.spectrace.validation.application.port.LabelSnapshotPort;
import com.spectrace.validation.domain.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotPortContractTest {

    @Test
    void snapshotCarriesPinnedFormulaComponentsAndDistinctDeclarationSemantics() {
        FormulaComponent matched = new FormulaComponent(
                "ing_soy_lecithin", "Soy lecithin", "canonical ingredient match", FormulaComponent.MatchStatus.MATCHED);
        FormulaItem item = new FormulaItem(
                "formula-item-1", "mat_soy_carrier", "spec_soy_carrier_v1", null, null, List.of(matched));
        LabelValidationSnapshot snapshot = new LabelValidationSnapshot(
                "label-1", true, "formula-1", "US", List.of(item), List.of(
                new DeclaredAllergen("all_soy", DeclaredAllergen.DeclarationType.CONTAINS),
                new DeclaredAllergen("all_milk", DeclaredAllergen.DeclarationType.MAY_CONTAIN)));

        assertThat(snapshot.formulaItems()).containsExactly(item);
        assertThat(snapshot.formulaItems().getFirst().components()).containsExactly(matched);
        assertThat(snapshot.declaredAllergens()).extracting(DeclaredAllergen::declarationType)
                .containsExactly(DeclaredAllergen.DeclarationType.CONTAINS, DeclaredAllergen.DeclarationType.MAY_CONTAIN);
        assertThatThrownBy(() -> snapshot.formulaItems().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.declaredAllergens().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unresolvedComponentsRemainExplicitAndMatchedComponentsRequireCanonicalIds() {
        assertThatCode(() -> new FormulaComponent(
                null, "unknown ingredient", "no canonical match", FormulaComponent.MatchStatus.UNMAPPED))
                .doesNotThrowAnyException();
        assertThatCode(() -> new FormulaComponent(
                null, "candidate ingredient", "two canonical matches", FormulaComponent.MatchStatus.AMBIGUOUS))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new FormulaComponent(
                null, "soy", "canonical ingredient match", FormulaComponent.MatchStatus.MATCHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ingredientId");
    }

    @Test
    void portsExposeTheSnapshotToDerivationAndPreserveLookupOutcome() {
        LabelValidationSnapshot snapshot = new LabelValidationSnapshot(
                "label-1", true, "formula-1", "US", List.of(), List.of());
        AllergenFactsPort factsPort = input -> {
            assertThat(input).isSameAs(snapshot);
            return List.of(new AllergenFact(
                    "all_soy", "SOY", AllergenFact.Presence.CONTAINS,
                    List.of("ing_soy_lecithin"), "rule_v1_soy_ingredient"));
        };

        assertThat(factsPort.deriveAllergenFacts(snapshot)).singleElement()
                .satisfies(fact -> {
                    assertThat(fact.presence()).isEqualTo(AllergenFact.Presence.CONTAINS);
                    assertThat(fact.sourceIngredientIds()).containsExactly("ing_soy_lecithin");
                });
        assertThat(LabelSnapshotLookup.found(snapshot).status()).isEqualTo(LabelSnapshotLookup.Status.FOUND);
        assertThat(LabelSnapshotLookup.notFound().status()).isEqualTo(LabelSnapshotLookup.Status.NOT_FOUND);
        assertThat(LabelSnapshotLookup.notCurrent().status()).isEqualTo(LabelSnapshotLookup.Status.NOT_CURRENT);
    }

    @Test
    void findingMatchesThePersistedValidationResultShapeAndAuthPortIsExplicit() {
        ValidationFinding finding = new ValidationFinding(
                "rule_v1_soy_ingredient", "SOY_DECLARATION_PRESENT", ValidationSeverity.INFO,
                true, false, "Soy declaration is present");

        assertThat(finding.ruleDefinitionId()).isEqualTo("rule_v1_soy_ingredient");
        assertThat(finding.resultCode()).isEqualTo("SOY_DECLARATION_PRESENT");
        assertThat(finding.passed()).isTrue();
        assertThat(finding.blocking()).isFalse();
        assertThat(AuthorizationPort.AuthorizationDecision.granted().allowed()).isTrue();
        assertThat(AuthorizationPort.AuthorizationDecision.denied("AUTHORIZATION_DENIED").denialCode())
                .isEqualTo("AUTHORIZATION_DENIED");
        assertThat(LabelSnapshotPort.class.getDeclaredMethods()).anySatisfy(method ->
                assertThat(method.getName()).isEqualTo("loadCurrentSnapshot"));
    }
}
