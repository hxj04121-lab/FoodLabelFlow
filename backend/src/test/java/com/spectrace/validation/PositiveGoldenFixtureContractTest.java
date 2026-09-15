package com.spectrace.validation;

import com.spectrace.support.fixture.PositiveGoldenFixtures;
import com.spectrace.support.fixture.PositiveGoldenFixtures.ExpectedFact;
import com.spectrace.support.fixture.PositiveGoldenFixtures.Fixture;
import com.spectrace.validation.application.contract.DeclaredAllergen.DeclarationType;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PositiveGoldenFixtureContractTest {

    @Test
    void fixtureRegistryAndSingleAllergenTruthAreExact() {
        assertThat(PositiveGoldenFixtures.ALL)
                .extracting(Fixture::fixtureId)
                .containsExactly(
                        PositiveGoldenFixtures.SOY_ID,
                        PositiveGoldenFixtures.MILK_ID,
                        PositiveGoldenFixtures.WHEAT_ID,
                        PositiveGoldenFixtures.MULTI_ID);

        assertSingle(
                PositiveGoldenFixtures.SOY_ID,
                "ing_s2_m2_soy_lecithin", "Soy lecithin", "SOY",
                "Contains: Soy", "SOY_DECLARATION_PRESENT");
        assertSingle(
                PositiveGoldenFixtures.MILK_ID,
                "ing_s2_m2_milk_powder", "Whole milk powder", "MILK",
                "Contains: Milk", "MILK_DECLARATION_PRESENT");
        assertSingle(
                PositiveGoldenFixtures.WHEAT_ID,
                "ing_s2_m2_wheat_flour", "Enriched wheat flour", "WHEAT",
                "Contains: Wheat", "WHEAT_DECLARATION_PRESENT");
    }

    @Test
    void multiItemTruthRetainsSourceOrderAndDeterministicFactOrder() {
        Fixture fixture = PositiveGoldenFixtures.byId(PositiveGoldenFixtures.MULTI_ID);

        assertThat(fixture.formulaItems())
                .extracting(item -> item.formulaItemId())
                .containsExactly("item_s2_m2_multi_01", "item_s2_m2_multi_02", "item_s2_m2_multi_03");
        assertThat(fixture.formulaItems())
                .flatExtracting(item -> item.components())
                .extracting(component -> component.ingredientId(), component -> component.rawPhrase())
                .containsExactly(
                        tuple("ing_s2_m2_wheat_flour", "Enriched wheat flour"),
                        tuple("ing_s2_m2_milk_powder", "Whole milk powder"),
                        tuple("ing_s2_m2_soy_lecithin", "Soy lecithin"));
        assertThat(fixture.expectedFacts())
                .extracting(ExpectedFact::allergenCode, ExpectedFact::presence)
                .containsExactly(
                        tuple("MILK", DeclarationType.CONTAINS),
                        tuple("SOY", DeclarationType.CONTAINS),
                        tuple("WHEAT", DeclarationType.CONTAINS));
        assertThat(fixture.declarations())
                .extracting(declaration -> declaration.allergenId())
                .containsExactly("all_milk", "all_soy", "all_wheat");
        assertThat(fixture.expectedLabelDeclaration()).isEqualTo("Contains: Milk, Soy, Wheat");
        assertThat(fixture.expectedFindings())
                .extracting(finding -> finding.resultCode())
                .containsExactly(
                        "MILK_DECLARATION_PRESENT",
                        "SOY_DECLARATION_PRESENT",
                        "WHEAT_DECLARATION_PRESENT");
    }

    @Test
    void everyPositiveFixtureBuildsCurrentPortValuesAndPassExpectations() {
        assertThat(DeclarationType.values())
                .containsExactly(DeclarationType.CONTAINS, DeclarationType.MAY_CONTAIN);

        for (Fixture fixture : PositiveGoldenFixtures.ALL) {
            assertThat(fixture.formulaSnapshot().formulaVersionId()).isEqualTo(fixture.formulaVersionId());
            assertThat(fixture.labelSnapshot().isCurrent()).isTrue();
            assertThat(fixture.labelSnapshot().ruleSetVersionId())
                    .isEqualTo(PositiveGoldenFixtures.RULE_SET_VERSION_ID);
            assertThat(fixture.expectedDerivation().unresolvedComponents()).isEmpty();
            assertThat(fixture.findings()).allSatisfy(finding -> {
                assertThat(finding.passed()).isTrue();
                assertThat(finding.blocking()).isFalse();
            });
            assertThat(fixture.expectedResult()).isEqualTo(ValidationStatus.PASSED);
            assertThat(fixture.expectedFacts()).allSatisfy(fact ->
                    assertThat(fixture.declarations())
                            .filteredOn(declaration -> declaration.allergenId().equals(fact.allergenId()))
                            .singleElement()
                            .extracting(declaration -> declaration.declarationType())
                            .isEqualTo(fact.presence()));
        }

        assertThatThrownBy(() -> PositiveGoldenFixtures.ALL.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PositiveGoldenFixtures.byId("missing-fixture"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown positive fixture");
    }

    @Test
    void mysqlResourceNamesEveryFixtureAndHasNoSeedFallbackOrSchemaChange() throws IOException {
        String sql = new ClassPathResource(PositiveGoldenFixtures.SQL_RESOURCE.substring(1))
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains(
                PositiveGoldenFixtures.SOY_ID,
                PositiveGoldenFixtures.MILK_ID,
                PositiveGoldenFixtures.WHEAT_ID,
                PositiveGoldenFixtures.MULTI_ID,
                "prov_s2_m2_positive_v1",
                "ruleset_s2_m2_positive_v1");
        assertThat(sql).doesNotContain(
                "INSERT IGNORE", "ON DUPLICATE KEY", "V3__baseline_seed",
                "CREATE TABLE", "ALTER TABLE", "validation_run", "validation_result");
    }

    private static void assertSingle(
            String fixtureId,
            String ingredientId,
            String rawPhrase,
            String allergenCode,
            String labelDeclaration,
            String resultCode
    ) {
        Fixture fixture = PositiveGoldenFixtures.byId(fixtureId);

        assertThat(fixture.formulaItems()).singleElement().satisfies(item ->
                assertThat(item.components()).singleElement().satisfies(component -> {
                    assertThat(component.ingredientId()).isEqualTo(ingredientId);
                    assertThat(component.rawPhrase()).isEqualTo(rawPhrase);
                }));
        assertThat(fixture.expectedFacts()).singleElement().satisfies(fact -> {
            assertThat(fact.allergenCode()).isEqualTo(allergenCode);
            assertThat(fact.presence()).isEqualTo(DeclarationType.CONTAINS);
            assertThat(fact.evidence()).singleElement().satisfies(evidence ->
                    assertThat(evidence.ingredientId()).isEqualTo(ingredientId));
        });
        assertThat(fixture.expectedLabelDeclaration()).isEqualTo(labelDeclaration);
        assertThat(fixture.declarations()).singleElement().satisfies(declaration -> {
            assertThat(declaration.declarationType()).isEqualTo(DeclarationType.CONTAINS);
            assertThat(declaration.displayText()).isEqualTo(labelDeclaration);
        });
        assertThat(fixture.expectedFindings()).singleElement().satisfies(finding ->
                assertThat(finding.resultCode()).isEqualTo(resultCode));
        assertThat(fixture.expectedResult()).isEqualTo(ValidationStatus.PASSED);
    }
}
