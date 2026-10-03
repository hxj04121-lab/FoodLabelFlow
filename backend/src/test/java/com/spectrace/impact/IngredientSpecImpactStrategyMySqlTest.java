package com.spectrace.impact;

import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.RelevantProductDiscovery;
import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.application.strategy.ImpactStrategy;
import com.spectrace.impact.application.strategy.ImpactStrategyRegistry;
import com.spectrace.impact.application.strategy.ProductImpactAssessment;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-78 on the Flyway V3 seed with the real catalog, allergen and label adapters.
 * Spec V2 and its N+1 adoption are fixtures (M2's adoption is not on main yet) and are
 * rolled back after each test. Expected outcomes come from M2's golden CSV.
 */
@SpringBootTest
@Transactional
class IngredientSpecImpactStrategyMySqlTest extends MySqlIntegrationTestSupport {
    private static final String GOLDEN = "/golden/s3-m2-soy-spec-v2-impact-v1.csv";
    private static final String CHOCOLATE = "mat_chocolate_base";
    private static final String SPEC_V1 = "spec_chocolate_v1";
    private static final String SPEC_V2 = "spec_chocolate_v2_scrum78";
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";

    @Autowired
    private RelevantProductDiscovery discovery;

    @Autowired
    private ImpactStrategyRegistry strategies;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theRegistrySelectsTheIngredientSpecStrategyAndNothingElse() {
        assertThat(strategies.require(ChangeType.INGREDIENT_SPEC).changeType()).isEqualTo(ChangeType.INGREDIENT_SPEC);
        assertThatThrownBy(() -> strategies.require(ChangeType.FORMULA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> strategies.require(ChangeType.RULE_SET)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soyLecithinInChocolateBaseClassifiesEveryRelevantProductAsTheGoldenExpects() throws IOException {
        Map<String, Set<String>> golden = golden();
        releaseSpecV2WithSoyLecithin();
        List<RelevantProductTarget> before = discovery.discover(CHOCOLATE);
        before.forEach(product -> adoptSpecV2(product.productId(), product.currentFormulaVersionId()));

        ImpactStrategy strategy = strategies.require(ChangeType.INGREDIENT_SPEC);
        List<RelevantProductTarget> products = discovery.discover(CHOCOLATE);
        Map<String, ProductImpactAssessment> byProduct = products.stream()
                .map(product -> strategy.assess(change(), product, RULE_SET))
                .collect(Collectors.toMap(ProductImpactAssessment::productId, assessment -> assessment));

        assertThat(byProduct.keySet()).doesNotContainAnyElementsOf(golden.get("EXCLUDED_NO_FINDING"));
        assertThat(productsWith(byProduct, ImpactClassification.NO_ACTION)).isEqualTo(golden.get("NO_ACTION"));
        assertThat(productsWith(byProduct, ImpactClassification.REVIEW_REQUIRED)).isEqualTo(golden.get("REVIEW_REQUIRED"));
        for (RelevantProductTarget product : products) {
            ProductImpactAssessment assessment = byProduct.get(product.productId());
            String previous = before.stream().filter(found -> found.productId().equals(product.productId()))
                    .findFirst().orElseThrow().currentFormulaVersionId();
            assertThat(assessment.currentFormulaVersionId()).isEqualTo(previous);
            assertThat(assessment.proposedFormulaVersionId()).isEqualTo(product.currentFormulaVersionId());
            assertThat(assessment.currentLabelVersionId()).isEqualTo(product.currentLabelVersionId());
            assertThat(assessment.missingAllergenCodes()).isEqualTo(
                    assessment.classification() == ImpactClassification.REVIEW_REQUIRED ? List.of("SOY") : List.of());
            assertThat(assessment.explanation()).hasSizeLessThanOrEqualTo(1000).contains(SPEC_V2);
        }
    }

    @Test
    void beforeAdoptionEveryRelevantProductIsAdoptionPendingAndNothingIsWritten() {
        releaseSpecV2WithSoyLecithin();
        ImpactStrategy strategy = strategies.require(ChangeType.INGREDIENT_SPEC);
        int findings = jdbc.queryForObject("SELECT COUNT(*) FROM impact_finding", Integer.class);

        for (RelevantProductTarget product : discovery.discover(CHOCOLATE)) {
            assertThatThrownBy(() -> strategy.assess(change(), product, RULE_SET))
                    .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(422);
                        assertThat(failure.code()).isEqualTo("FORMULA_ADOPTION_PENDING");
                    });
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM impact_finding", Integer.class)).isEqualTo(findings);
    }

    private void releaseSpecV2WithSoyLecithin() {
        jdbc.update("""
                INSERT INTO ingredient_specification_version(specification_version_id, supplier_material_id,
                    version_number, lifecycle_status, effective_date, released_at, created_by_user_id, data_provenance_id)
                VALUES (?, ?, 2, 'RELEASED', '2026-09-01', '2026-09-01 09:00:00', 'user_admin', 'prov_project_seed')
                """, SPEC_V2, CHOCOLATE);
        jdbc.update("""
                INSERT INTO spec_component(spec_component_id, specification_version_id, ingredient_id, raw_phrase,
                                           match_rule, match_status, sequence_no)
                SELECT CONCAT(spec_component_id, '_scrum78'), ?, ingredient_id, raw_phrase, match_rule,
                       match_status, sequence_no
                FROM spec_component WHERE specification_version_id = ?
                """, SPEC_V2, SPEC_V1);
        jdbc.update("""
                INSERT INTO spec_component(spec_component_id, specification_version_id, ingredient_id, raw_phrase,
                                           match_rule, match_status, sequence_no)
                VALUES ('sc_chocolate_v2_scrum78_soy', ?, 'ing_soy_lecithin', 'Soy lecithin',
                        'Project fixture component', 'MATCHED', 3)
                """, SPEC_V2);
    }

    /** Formula N+1: N's items with Chocolate Base pinned to Spec V2, released as current. */
    private void adoptSpecV2(String productId, String formulaN) {
        String formulaN1 = formulaN + "_scrum78";
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", formulaN);
        jdbc.update("""
                INSERT INTO formula_version(formula_version_id, product_id, version_number, lifecycle_status,
                    is_current_released, created_by_user_id, released_by_user_id, released_at, data_provenance_id)
                SELECT ?, product_id, version_number + 1, 'RELEASED', 'Y', 'user_admin', 'user_admin',
                       '2026-09-02 09:00:00', 'prov_project_seed'
                FROM formula_version WHERE formula_version_id = ?
                """, formulaN1, formulaN);
        jdbc.update("""
                INSERT INTO formula_item(formula_item_id, formula_version_id, supplier_material_id,
                    specification_version_id, sequence_no, quantity_value, quantity_unit)
                SELECT CONCAT(formula_item_id, '_scrum78'), ?, supplier_material_id,
                       CASE WHEN supplier_material_id = ? THEN ? ELSE specification_version_id END,
                       sequence_no, quantity_value, quantity_unit
                FROM formula_item WHERE formula_version_id = ?
                """, formulaN1, CHOCOLATE, SPEC_V2, formulaN);
        jdbc.update("UPDATE product SET current_formula_version_id = ? WHERE product_id = ?", formulaN1, productId);
    }

    private static Set<String> productsWith(
            Map<String, ProductImpactAssessment> byProduct, ImpactClassification classification) {
        return byProduct.values().stream()
                .filter(assessment -> assessment.classification() == classification)
                .map(ProductImpactAssessment::productId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static ChangeRequest change() {
        return new ChangeRequest("cr-scrum78", "CR-scrum78", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED,
                Instant.parse("2026-10-01T00:00:00Z"), "user_change_manager",
                "Chocolate Base Spec V2 adds Soy Lecithin", new VersionChange(SPEC_V1, SPEC_V2), "prov_scenario_input");
    }

    /** outcome → product IDs from M2's versioned golden (S3-M2-SOY-SPEC-V2-IMPACT v1). */
    private static Map<String, Set<String>> golden() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                IngredientSpecImpactStrategyMySqlTest.class.getResourceAsStream(GOLDEN), GOLDEN),
                StandardCharsets.UTF_8))) {
            Map<String, Set<String>> byOutcome = reader.lines().skip(1)
                    .map(line -> line.split(",", -1))
                    .collect(Collectors.groupingBy(columns -> columns[9],
                            Collectors.mapping(columns -> columns[10], Collectors.toCollection(TreeSet::new))));
            assertThat(byOutcome.keySet()).containsExactlyInAnyOrder("NO_ACTION", "REVIEW_REQUIRED", "EXCLUDED_NO_FINDING");
            assertThat(byOutcome.values()).allSatisfy(products -> assertThat(products).hasSize(20));
            return byOutcome;
        }
    }
}
