package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.validation.application.LabelAllergenQueryService;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabelAllergenQueryServiceTest {
    private final PositiveGoldenFixtures.Fixture fixture = PositiveGoldenFixtures.byId(PositiveGoldenFixtures.SOY_ID);
    private final LabelSnapshotPort labels = mock(LabelSnapshotPort.class);
    private final FormulaCompositionPort formulas = mock(FormulaCompositionPort.class);
    private final RuleSetVersionRepository ruleSets = mock(RuleSetVersionRepository.class);
    private final AllergenFactsPort allergens = mock(AllergenFactsPort.class);
    private final LabelAllergenQueryService service = new LabelAllergenQueryService(labels, formulas, ruleSets, allergens);

    @BeforeEach
    void validPinnedInputs() {
        when(labels.findById(fixture.labelVersionId())).thenReturn(Optional.of(fixture.labelSnapshot()));
        when(formulas.findById(fixture.formulaVersionId())).thenReturn(Optional.of(fixture.formulaSnapshot()));
        when(ruleSets.findById(fixture.ruleSetVersionId())).thenReturn(Optional.of(new RuleSetVersion(
                fixture.ruleSetVersionId(), "EXACT_FIXTURE", "1", "US", RuleSetLifecycleStatus.RETIRED,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), true, "Retired display fixture", "fixture", List.of())));
        when(allergens.derive(any(), anyString(), anyString())).thenReturn(fixture.expectedDerivation());
    }

    @Test
    void readsRetiredExpiredRuleSetWithoutEvaluatingOrSubstitutingIt() {
        var value = service.getByLabelVersionId(fixture.labelVersionId());
        assertThat(value.labelVersionId()).isEqualTo(fixture.labelVersionId());
        assertThat(value.ruleSetVersionId()).isEqualTo(fixture.ruleSetVersionId());
        assertThat(value.facts()).isEqualTo(fixture.expectedDerivation().facts());
        verify(allergens).derive(fixture.formulaSnapshot(), fixture.ruleSetVersionId(), fixture.jurisdictionCode());
        verify(ruleSets, never()).findActiveById(anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankLabelId(String id) {
        assertThatThrownBy(() -> service.getByLabelVersionId(id)).isInstanceOfSatisfying(ValidationFailure.class,
                failure -> assertThat(failure.status()).isEqualTo(400));
    }

    @Test
    void missingLabelIsNotFound() {
        when(labels.findById(fixture.labelVersionId())).thenReturn(Optional.empty());
        assertFailure(404);
    }

    @Test
    void missingFormulaIsNotAnEmptyDerivation() {
        when(formulas.findById(fixture.formulaVersionId())).thenReturn(Optional.empty());
        assertFailure(422);
    }

    @Test
    void missingPinnedRuleSetIsNotSubstituted() {
        when(ruleSets.findById(fixture.ruleSetVersionId())).thenReturn(Optional.empty());
        assertFailure(422);
    }

    @Test
    void rejectsFormulaForAnotherProduct() {
        when(formulas.findById(fixture.formulaVersionId())).thenReturn(Optional.of(new FormulaCompositionSnapshot(
                "another-product", fixture.formulaVersionId(), false, fixture.formulaSnapshot().items())));
        assertFailure(422);
    }

    @Test
    void rejectsIncompleteSpecificationComponents() {
        var item = fixture.formulaSnapshot().items().getFirst();
        when(formulas.findById(fixture.formulaVersionId())).thenReturn(Optional.of(new FormulaCompositionSnapshot(
                fixture.productId(), fixture.formulaVersionId(), true, List.of(new FormulaCompositionSnapshot.Item(
                item.formulaItemId(), item.supplierMaterialId(), item.specificationVersionId(), List.of())))));
        assertFailure(422);
    }

    @Test
    void rejectsLabelPortReturningAnotherVersion() {
        var label = fixture.labelSnapshot();
        when(labels.findById(fixture.labelVersionId())).thenReturn(Optional.of(new LabelValidationSnapshot(
                "wrong-label", label.productId(), label.formulaVersionId(), label.ruleSetVersionId(),
                label.jurisdictionCode(), label.rawIngredientText(), false, label.dataProvenanceId(), label.declarations())));
        assertFailure(500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"formula", "ruleSet", "jurisdiction"})
    void rejectsMixedVersionDerivationFromAdapter(String mismatch) {
        var expected = fixture.expectedDerivation();
        when(allergens.derive(any(), anyString(), anyString())).thenReturn(new AllergenDerivation(
                mismatch.equals("formula") ? "wrong-formula" : expected.formulaVersionId(),
                mismatch.equals("ruleSet") ? "wrong-rules" : expected.ruleSetVersionId(),
                mismatch.equals("jurisdiction") ? "EU" : expected.jurisdictionCode(),
                expected.facts(), expected.unresolvedComponents()));
        assertThatThrownBy(() -> service.getByLabelVersionId(fixture.labelVersionId()))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> assertThat(failure.status()).isEqualTo(500));
    }

    private void assertFailure(int status) {
        assertThatThrownBy(() -> service.getByLabelVersionId(fixture.labelVersionId()))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> assertThat(failure.status()).isEqualTo(status));
        verify(allergens, never()).derive(any(), anyString(), anyString());
    }
}
