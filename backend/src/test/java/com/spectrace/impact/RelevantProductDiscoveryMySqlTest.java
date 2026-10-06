package com.spectrace.impact;

import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import com.spectrace.catalog.application.port.RelevantProductLookupPort.RelevantProduct;
import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.RelevantProductDiscovery;
import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-77 against the Flyway V3 seed. The seed's fixture_group is the oracle only;
 * the lookup itself never reads it. Each test rolls back its fixture rows.
 */
@SpringBootTest
@Transactional
class RelevantProductDiscoveryMySqlTest extends MySqlIntegrationTestSupport {
    private static final String CHOCOLATE = "mat_chocolate_base";

    @Autowired
    private RelevantProductDiscovery discovery;

    @Autowired
    private RelevantProductLookupPort lookup;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void chocolateBaseMatchesExactlyTheFortyRelevantSeedProducts() {
        Set<String> relevant = seedGroup("NO_ACTION_BASELINE_SOY", "REVIEW_REQUIRED_BASELINE_NO_SOY");
        Set<String> negativeControls = seedGroup("NEGATIVE_CONTROL_NO_CHOCOLATE");
        assertThat(relevant).hasSize(40);
        assertThat(negativeControls).hasSize(20);

        List<RelevantProductTarget> targets = discovery.discover(CHOCOLATE);

        assertThat(targets).extracting(RelevantProductTarget::productId)
                .containsExactlyElementsOf(relevant)
                .doesNotContainAnyElementsOf(negativeControls);
        Map<String, Map<String, Object>> pointers = jdbc.queryForList("""
                        SELECT product_id, current_formula_version_id, current_published_label_version_id
                        FROM product
                        """).stream()
                .collect(Collectors.toMap(row -> (String) row.get("product_id"), row -> row));
        for (RelevantProductTarget target : targets) {
            var pointer = pointers.get(target.productId());
            assertThat(target.currentFormulaVersionId()).isEqualTo(pointer.get("current_formula_version_id"));
            assertThat(target.currentLabelVersionId()).isEqualTo(pointer.get("current_published_label_version_id"));
            assertThat(target.matchingFormulaItemIds()).allSatisfy(itemId -> assertThat(jdbc.queryForObject(
                    "SELECT supplier_material_id FROM formula_item WHERE formula_item_id = ? AND formula_version_id = ?",
                    String.class, itemId, target.currentFormulaVersionId())).isEqualTo(CHOCOLATE));
        }
    }

    @Test
    void negativeControlsNeverEnterTheRunEvenThroughNonCurrentFormulaVersions() {
        String control = seedGroup("NEGATIVE_CONTROL_NO_CHOCOLATE").iterator().next();
        insertFormula(control, 2, "RELEASED", "N");
        insertItem(control, 2, CHOCOLATE, "spec_chocolate_v1");
        insertFormula(control, 3, "DRAFT", "N");
        insertItem(control, 3, CHOCOLATE, "spec_chocolate_v1");
        insertFormula(control, 4, "RETIRED", "N");
        insertItem(control, 4, CHOCOLATE, "spec_chocolate_v1");

        assertThat(discovery.discover(CHOCOLATE)).extracting(RelevantProductTarget::productId)
                .doesNotContain(control)
                .hasSize(40);
    }

    @Test
    void aProductWhoseCurrentFormulaDroppedTheMaterialIsNoLongerRelevant() {
        String product = seedGroup("REVIEW_REQUIRED_BASELINE_NO_SOY").iterator().next();
        String v1 = formulaId(product, 1);
        // Same shape as sp_release_formula_version, without its inner COMMIT, so the test can roll back.
        jdbc.update("UPDATE formula_version SET is_current_released = 'N' WHERE formula_version_id = ?", v1);
        insertFormula(product, 2, "RELEASED", "Y");
        insertItem(product, 2, "mat_wheat_flour", "spec_wheat_flour_v1");
        jdbc.update("UPDATE product SET current_formula_version_id = ? WHERE product_id = ?",
                formulaId(product, 2), product);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formula_item WHERE formula_version_id = ? AND supplier_material_id = ?",
                Integer.class, v1, CHOCOLATE)).isPositive();

        assertThat(discovery.discover(CHOCOLATE)).extracting(RelevantProductTarget::productId)
                .doesNotContain(product)
                .hasSize(39);
    }

    @Test
    void aRelevantProductWithoutAPublishedLabelIsReportedAndFailsDiscoveryBeforeAnyWrite() {
        Set<String> relevant = seedGroup("NO_ACTION_BASELINE_SOY");
        String unpublished = relevant.iterator().next();
        String superseded = relevant.stream().skip(1).findFirst().orElseThrow();
        jdbc.update("UPDATE product SET current_published_label_version_id = NULL WHERE product_id = ?", unpublished);
        // A pointer to a label that is no longer the current published one is not a published label.
        jdbc.update("""
                UPDATE label_version SET lifecycle_status = 'SUPERSEDED', is_current_published = 'N'
                WHERE product_id = ?
                """, superseded);
        int findingsBefore = count("impact_finding");

        Map<String, RelevantProduct> reported = lookup.findProductsUsingMaterial(CHOCOLATE).stream()
                .collect(Collectors.toMap(RelevantProduct::productId, product -> product));
        assertThat(reported).hasSize(40);
        assertThat(reported.get(unpublished).hasPublishedLabel()).isFalse();
        assertThat(reported.get(superseded).hasPublishedLabel()).isFalse();

        assertThatThrownBy(() -> discovery.discover(CHOCOLATE))
                .isInstanceOfSatisfying(ImpactFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("PUBLISHED_LABEL_MISSING");
                    assertThat(failure.getMessage()).contains(unpublished, superseded);
                });
        assertThat(count("impact_finding")).isEqualTo(findingsBefore);
    }

    @Test
    void everyItemUsingTheMaterialInTheCurrentFormulaIsReported() {
        String product = seedGroup("NO_ACTION_BASELINE_SOY").iterator().next();
        String formula = formulaId(product, 1);
        jdbc.update("""
                INSERT INTO formula_item(formula_item_id, formula_version_id, supplier_material_id,
                                         specification_version_id, sequence_no)
                VALUES (?, ?, ?, 'spec_chocolate_v1', 99)
                """, "fi_scrum77_second_chocolate", formula, CHOCOLATE);

        RelevantProductTarget target = discovery.discover(CHOCOLATE).stream()
                .filter(found -> found.productId().equals(product)).findFirst().orElseThrow();

        assertThat(target.matchingFormulaItemIds()).hasSize(2).last().isEqualTo("fi_scrum77_second_chocolate");
    }

    @Test
    void anUnusedMaterialIsACompletedEmptyResult() {
        assertThat(discovery.discover("mat_unused_by_any_formula")).isEmpty();
    }

    private Set<String> seedGroup(String... fixtureGroups) {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT product_id FROM product WHERE fixture_group IN ("
                        + String.join(", ", Collections.nCopies(fixtureGroups.length, "?")) + ")",
                String.class, (Object[]) fixtureGroups));
    }

    private String formulaId(String productId, int version) {
        if (version == 1) {
            return jdbc.queryForObject(
                    "SELECT formula_version_id FROM formula_version WHERE product_id = ? AND version_number = 1",
                    String.class, productId);
        }
        return "formula_scrum77_" + productId + "_v" + version;
    }

    private void insertFormula(String productId, int version, String lifecycle, String current) {
        boolean released = !"DRAFT".equals(lifecycle);
        jdbc.update("""
                INSERT INTO formula_version(formula_version_id, product_id, version_number, lifecycle_status,
                                            is_current_released, created_by_user_id, released_by_user_id,
                                            released_at, data_provenance_id)
                VALUES (?, ?, ?, ?, ?, 'user_admin', ?, ?, 'prov_project_seed')
                """, formulaId(productId, version), productId, version, lifecycle, current,
                released ? "user_admin" : null, released ? "2026-09-01 09:00:00" : null);
    }

    private void insertItem(String productId, int version, String materialId, String specificationVersionId) {
        String formula = formulaId(productId, version);
        jdbc.update("""
                INSERT INTO formula_item(formula_item_id, formula_version_id, supplier_material_id,
                                         specification_version_id, sequence_no)
                VALUES (?, ?, ?, ?, 1)
                """, "fi_" + formula + "_1", formula, materialId, specificationVersionId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
