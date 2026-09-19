package com.spectrace.validation;

import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import com.spectrace.shared.api.ApiError;
import com.spectrace.support.fixture.NegativeGoldenFixtures;
import com.spectrace.support.fixture.NegativeGoldenFixtures.FailureFamily;
import com.spectrace.support.fixture.NegativeGoldenFixtures.Fixture;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class NegativeGoldenFixtureContractTest {

    @Test
    void registryContainsExactlyTheFourRequiredFailureFamilies() {
        assertThat(NegativeGoldenFixtures.ALL)
                .extracting(Fixture::fixtureId, Fixture::family)
                .containsExactly(
                        tuple(NegativeGoldenFixtures.MISSING_DECLARATION_ID,
                                FailureFamily.MISSING_ALLERGEN_DECLARATION),
                        tuple(NegativeGoldenFixtures.NO_ACTIVE_RULE_SET_ID,
                                FailureFamily.NO_ACTIVE_RULE_SET),
                        tuple(NegativeGoldenFixtures.UNMAPPED_INGREDIENT_ID,
                                FailureFamily.UNMAPPED_INGREDIENT),
                        tuple(NegativeGoldenFixtures.AMBIGUOUS_INGREDIENT_ID,
                                FailureFamily.AMBIGUOUS_INGREDIENT));

        assertThatThrownBy(() -> NegativeGoldenFixtures.ALL.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> NegativeGoldenFixtures.byId("missing-fixture"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown negative fixture");
    }

    @Test
    void missingDeclarationRetainsSoyFactAndFailsAsBlockingCompletedRun() {
        Fixture fixture = NegativeGoldenFixtures.byId(
                NegativeGoldenFixtures.MISSING_DECLARATION_ID);

        assertThat(fixture.labelSnapshot().declarations()).isEmpty();
        assertThat(fixture.expectedDerivation().facts()).singleElement().satisfies(fact -> {
            assertThat(fact.allergenId()).isEqualTo("all_s2_m2_neg_soy");
            assertThat(fact.allergenCode()).isEqualTo("SOY");
            assertThat(fact.derivationEvidence()).singleElement().satisfies(evidence -> {
                assertThat(evidence.formulaItemId()).isEqualTo("item_s2_m2_neg_missing_decl_v1");
                assertThat(evidence.ingredientId()).isEqualTo("ing_s2_m2_neg_soy_lecithin");
            });
        });
        assertThat(fixture.expectedDerivation().unresolvedComponents()).isEmpty();
        assertThat(fixture.expectedFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.ruleDefinitionId()).isEqualTo("rule_s2_m2_neg_soy_decl");
            assertThat(finding.resultCode()).isEqualTo("ALLERGEN_DECLARATION_MISSING");
            assertThat(finding.severity()).isEqualTo(ValidationSeverity.ERROR);
            assertThat(finding.passed()).isFalse();
            assertThat(finding.blocking()).isTrue();
        });
        assertCompletedFailure(fixture, "ALLERGEN_DECLARATION_MISSING");
    }

    @Test
    void inactiveExactRuleSetIs422EvenWhenFactsAndDeclarationWouldOtherwiseMatch() {
        Fixture fixture = NegativeGoldenFixtures.byId(
                NegativeGoldenFixtures.NO_ACTIVE_RULE_SET_ID);

        assertThat(fixture.activeRuleSetExpected()).isFalse();
        assertThat(fixture.labelSnapshot().ruleSetVersionId())
                .isEqualTo(NegativeGoldenFixtures.INACTIVE_RULE_SET_VERSION_ID);
        assertThat(fixture.labelSnapshot().declarations()).singleElement().satisfies(declaration -> {
            assertThat(declaration.allergenId()).isEqualTo("all_s2_m2_neg_soy");
            assertThat(declaration.declarationType()).isEqualTo("CONTAINS");
            assertThat(declaration.displayText()).isEqualTo("Contains: Soy");
        });
        assertThat(fixture.expectedDerivation().facts()).singleElement().satisfies(fact ->
                assertThat(fact.allergenCode()).isEqualTo("SOY"));
        assertThat(fixture.expectedDerivation().unresolvedComponents()).isEmpty();
        assertThat(fixture.expectedFindings()).isEmpty();

        assertThat(fixture.expectedOutcome().passed()).isFalse();
        assertThat(fixture.expectedOutcome().blocking()).isTrue();
        assertThat(fixture.expectedOutcome().completedRunStatus()).isNull();
        assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(422);
        assertThat(fixture.expectedOutcome().code()).isEqualTo("VALIDATION_PRECONDITION_FAILED");
        assertThat(fixture.expectedOutcome().apiError()).isEqualTo(new ApiError(
                "VALIDATION_PRECONDITION_FAILED",
                "The current label has no active rule-set available for evaluation.",
                null,
                null));
        assertThat(ApiError.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("code", "message", "traceId", "evidenceId");
    }

    @Test
    void unmappedAndAmbiguousInputsRemainExplicitBlockingFindings() {
        assertUnresolved(
                NegativeGoldenFixtures.UNMAPPED_INGREDIENT_ID,
                MatchStatus.UNMAPPED,
                "Mystery protein blend",
                "INGREDIENT_UNMAPPED");
        assertUnresolved(
                NegativeGoldenFixtures.AMBIGUOUS_INGREDIENT_ID,
                MatchStatus.AMBIGUOUS,
                "Natural flavor concentrate",
                "INGREDIENT_AMBIGUOUS");
    }

    @Test
    void noNegativeFixtureCanSilentlyBecomeAPass() {
        for (Fixture fixture : NegativeGoldenFixtures.ALL) {
            assertThat(fixture.labelSnapshot().isCurrent()).isTrue();
            assertThat(fixture.formulaSnapshot().isCurrentReleased()).isTrue();
            assertThat(fixture.expectedOutcome().passed()).isFalse();
            assertThat(fixture.expectedOutcome().blocking()).isTrue();

            if (fixture.expectedOutcome().completedRunStatus() != null) {
                assertThat(fixture.expectedOutcome().completedRunStatus())
                        .isEqualTo(ValidationStatus.FAILED);
                assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(201);
                assertThat(fixture.expectedOutcome().apiError()).isNull();
                assertThat(fixture.expectedFindings()).allSatisfy(finding -> {
                    assertThat(finding.passed()).isFalse();
                    assertThat(finding.blocking()).isTrue();
                    assertThat(finding.severity()).isEqualTo(ValidationSeverity.ERROR);
                });
            } else {
                assertThat(fixture.expectedFindings()).isEmpty();
                assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(422);
                assertThat(fixture.expectedOutcome().apiError()).isNotNull();
            }
        }
    }

    @Test
    void mysqlResourceIsSelfContainedAndHasNoSeedOrConflictFallback() throws IOException {
        String sql = new ClassPathResource(NegativeGoldenFixtures.SQL_RESOURCE.substring(1))
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                NegativeGoldenFixtures.MISSING_DECLARATION_ID,
                NegativeGoldenFixtures.NO_ACTIVE_RULE_SET_ID,
                NegativeGoldenFixtures.UNMAPPED_INGREDIENT_ID,
                NegativeGoldenFixtures.AMBIGUOUS_INGREDIENT_ID,
                NegativeGoldenFixtures.MISSING_DECLARATION_RULE_SET_VERSION_ID,
                NegativeGoldenFixtures.UNMAPPED_RULE_SET_VERSION_ID,
                NegativeGoldenFixtures.AMBIGUOUS_RULE_SET_VERSION_ID,
                NegativeGoldenFixtures.INACTIVE_RULE_SET_VERSION_ID,
                "'UNMAPPED'",
                "'AMBIGUOUS'",
                "'RETIRED'");
        assertThat(sql).doesNotContain(
                "INSERT IGNORE",
                "ON DUPLICATE KEY",
                "V3__baseline_seed",
                "CREATE TABLE",
                "ALTER TABLE",
                "validation_run",
                "validation_result");
    }

    private static void assertUnresolved(
            String fixtureId,
            MatchStatus matchStatus,
            String rawPhrase,
            String resultCode
    ) {
        Fixture fixture = NegativeGoldenFixtures.byId(fixtureId);

        assertThat(fixture.labelSnapshot().declarations()).isEmpty();
        assertThat(fixture.expectedDerivation().facts()).isEmpty();
        assertThat(fixture.expectedDerivation().unresolvedComponents())
                .singleElement()
                .satisfies(component -> {
                    assertThat(component.matchStatus()).isEqualTo(matchStatus);
                    assertThat(component.rawPhrase()).isEqualTo(rawPhrase);
                });
        assertThat(fixture.expectedFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.ruleDefinitionId()).isEqualTo(
                    matchStatus == MatchStatus.UNMAPPED
                            ? "rule_s2_m2_neg_unmapped"
                            : "rule_s2_m2_neg_ambiguous");
            assertThat(finding.resultCode()).isEqualTo(resultCode);
            assertThat(finding.passed()).isFalse();
            assertThat(finding.blocking()).isTrue();
        });
        assertCompletedFailure(fixture, resultCode);
    }

    private static void assertCompletedFailure(Fixture fixture, String resultCode) {
        assertThat(fixture.activeRuleSetExpected()).isTrue();
        assertThat(fixture.expectedOutcome().passed()).isFalse();
        assertThat(fixture.expectedOutcome().blocking()).isTrue();
        assertThat(fixture.expectedOutcome().code()).isEqualTo(resultCode);
        assertThat(fixture.expectedOutcome().httpStatus()).isEqualTo(201);
        assertThat(fixture.expectedOutcome().completedRunStatus()).isEqualTo(ValidationStatus.FAILED);
        assertThat(fixture.expectedOutcome().apiError()).isNull();
    }
}
