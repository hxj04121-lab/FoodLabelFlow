package com.spectrace.impact;

import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class S3SoyGoldenRelationshipMySqlTest extends MySqlIntegrationTestSupport {

    private static final String GOLDEN_RESOURCE = "/golden/s3-m2-soy-spec-v2-impact-v1.csv";
    private static final String GOLDEN_HEADER = "golden_id,version,status,changed_supplier_material_id,"
            + "baseline_specification_version_id,added_ingredient_id,added_ingredient_name,"
            + "candidate_specification_version_id,synthetic_mapping_used,outcome,product_id";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void goldenOutcomeSetsMatchCurrentFormulaAndPublishedLabelRelationships() throws IOException {
        GoldenScenario golden = readGolden();
        String changedMaterialId = golden.changedSupplierMaterialId();
        String baselineSpecificationId = golden.baselineSpecificationVersionId();
        String addedIngredientId = golden.addedIngredientId();

        assertThat(golden.goldenId()).isEqualTo("S3-M2-SOY-SPEC-V2-IMPACT");
        assertThat(golden.version()).isEqualTo(1);
        assertThat(golden.status()).isEqualTo("PROPOSED_SCENARIO_NOT_A_RELEASED_SPECIFICATION");
        assertThat(golden.candidateSpecificationVersionId()).isEmpty();
        assertThat(golden.syntheticMappingUsed()).isFalse();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ingredient_specification_version
                WHERE specification_version_id = ?
                  AND supplier_material_id = ?
                  AND lifecycle_status = 'RELEASED'
                """, Integer.class, baselineSpecificationId, changedMaterialId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM spec_component
                WHERE specification_version_id = ?
                  AND ingredient_id = ?
                """, Integer.class, baselineSpecificationId, addedIngredientId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ingredient_allergen ia
                JOIN allergen a ON a.allergen_id = ia.allergen_id
                WHERE ia.ingredient_id = ?
                  AND a.allergen_code = 'SOY'
                """, Integer.class, addedIngredientId)).isGreaterThan(0);

        Set<String> noActionIds = golden.noActionIds();
        Set<String> reviewRequiredIds = golden.reviewRequiredIds();
        Set<String> negativeControlIds = golden.negativeControlIds();
        Set<String> expectedRelevantIds = new TreeSet<>(noActionIds);
        expectedRelevantIds.addAll(reviewRequiredIds);

        List<RelevantProduct> relevantProducts = jdbc.query("""
                SELECT p.product_id,
                       fv.formula_version_id,
                       fv.lifecycle_status AS formula_status,
                       fv.is_current_released,
                       fi.specification_version_id,
                       lv.label_version_id,
                       lv.lifecycle_status AS label_status,
                       lv.is_current_published,
                       EXISTS (
                           SELECT 1
                           FROM formula_item soy_item
                           JOIN supplier_material soy_material
                             ON soy_material.supplier_material_id = soy_item.supplier_material_id
                           JOIN ingredient_allergen soy_link
                             ON soy_link.ingredient_id = soy_material.ingredient_id
                           JOIN allergen soy_allergen
                             ON soy_allergen.allergen_id = soy_link.allergen_id
                           WHERE soy_item.formula_version_id = fv.formula_version_id
                             AND soy_allergen.allergen_code = 'SOY'
                       ) AS formula_has_soy,
                       EXISTS (
                           SELECT 1
                           FROM label_allergen_declaration declaration
                           JOIN allergen declared_allergen
                             ON declared_allergen.allergen_id = declaration.allergen_id
                           WHERE declaration.label_version_id = lv.label_version_id
                             AND declaration.declaration_type = 'CONTAINS'
                             AND declared_allergen.allergen_code = 'SOY'
                       ) AS label_declares_soy
                FROM product p
                JOIN formula_version fv
                  ON fv.formula_version_id = p.current_formula_version_id
                 AND fv.product_id = p.product_id
                JOIN formula_item fi
                  ON fi.formula_version_id = fv.formula_version_id
                 AND fi.supplier_material_id = ?
                JOIN ingredient_specification_version spec
                  ON spec.specification_version_id = fi.specification_version_id
                 AND spec.supplier_material_id = fi.supplier_material_id
                JOIN label_version lv
                  ON lv.label_version_id = p.current_published_label_version_id
                 AND lv.product_id = p.product_id
                 AND lv.formula_version_id = fv.formula_version_id
                WHERE fi.specification_version_id = ?
                ORDER BY p.product_id
                """, (rs, rowNum) -> new RelevantProduct(
                rs.getString("product_id"),
                rs.getString("formula_version_id"),
                rs.getString("formula_status"),
                rs.getString("is_current_released"),
                rs.getString("specification_version_id"),
                rs.getString("label_version_id"),
                rs.getString("label_status"),
                rs.getString("is_current_published"),
                rs.getBoolean("formula_has_soy"),
                rs.getBoolean("label_declares_soy")
        ), changedMaterialId, baselineSpecificationId);

        Map<String, RelevantProduct> relevantByProduct = relevantProducts.stream()
                .collect(Collectors.toMap(RelevantProduct::productId, product -> product));
        assertThat(relevantByProduct.keySet()).isEqualTo(expectedRelevantIds);
        assertThat(relevantByProduct).hasSize(relevantProducts.size());

        noActionIds.forEach(productId -> {
            RelevantProduct product = relevantByProduct.get(productId);
            assertThat(product).isNotNull();
            assertCurrentReleasedFormulaAndPublishedLabel(product, baselineSpecificationId);
            assertThat(product.formulaHasSoy()).as(productId + " formula already contains SOY").isTrue();
            assertThat(product.labelDeclaresSoy()).as(productId + " published label declares SOY").isTrue();
        });
        reviewRequiredIds.forEach(productId -> {
            RelevantProduct product = relevantByProduct.get(productId);
            assertThat(product).isNotNull();
            assertCurrentReleasedFormulaAndPublishedLabel(product, baselineSpecificationId);
            assertThat(product.formulaHasSoy()).as(productId + " baseline formula has no SOY").isFalse();
            assertThat(product.labelDeclaresSoy()).as(productId + " published label does not declare SOY").isFalse();
        });

        String placeholders = String.join(",", negativeControlIds.stream().map(ignored -> "?").toList());
        List<Object> queryArguments = new ArrayList<>();
        queryArguments.add(changedMaterialId);
        queryArguments.addAll(negativeControlIds);
        String controlsSql = """
                SELECT p.product_id,
                       p.fixture_group,
                       fv.formula_version_id,
                       fv.lifecycle_status AS formula_status,
                       fv.is_current_released,
                       SUM(CASE WHEN fi.supplier_material_id = ? THEN 1 ELSE 0 END) AS changed_material_items
                FROM product p
                JOIN formula_version fv
                  ON fv.formula_version_id = p.current_formula_version_id
                 AND fv.product_id = p.product_id
                LEFT JOIN formula_item fi
                  ON fi.formula_version_id = fv.formula_version_id
                WHERE p.product_id IN (%s)
                GROUP BY p.product_id, p.fixture_group, fv.formula_version_id,
                         fv.lifecycle_status, fv.is_current_released
                ORDER BY p.product_id
                """.formatted(placeholders);
        List<NegativeControl> controls = jdbc.query(controlsSql,
                (rs, rowNum) -> new NegativeControl(
                        rs.getString("product_id"),
                        rs.getString("fixture_group"),
                        rs.getString("formula_version_id"),
                        rs.getString("formula_status"),
                        rs.getString("is_current_released"),
                        rs.getInt("changed_material_items")
                ), queryArguments.toArray());

        Map<String, NegativeControl> controlsByProduct = controls.stream()
                .collect(Collectors.toMap(NegativeControl::productId, control -> control));
        assertThat(controlsByProduct.keySet()).isEqualTo(negativeControlIds);
        negativeControlIds.forEach(productId -> {
            NegativeControl control = controlsByProduct.get(productId);
            assertThat(control.fixtureGroup()).isEqualTo("NEGATIVE_CONTROL_NO_CHOCOLATE");
            assertThat(control.formulaStatus()).isEqualTo("RELEASED");
            assertThat(control.currentReleased()).isEqualTo("Y");
            assertThat(control.changedMaterialItems()).isZero();
        });
    }

    private GoldenScenario readGolden() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(GOLDEN_RESOURCE), StandardCharsets.UTF_8))) {
            assertThat(reader.readLine()).isEqualTo(GOLDEN_HEADER);
            String[] metadata = null;
            Set<String> noActionIds = new TreeSet<>();
            Set<String> reviewRequiredIds = new TreeSet<>();
            Set<String> negativeControlIds = new TreeSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", -1);
                assertThat(columns).hasSize(11);
                String[] rowMetadata = new String[9];
                System.arraycopy(columns, 0, rowMetadata, 0, rowMetadata.length);
                if (metadata == null) {
                    metadata = rowMetadata;
                } else {
                    assertThat(rowMetadata).isEqualTo(metadata);
                }
                switch (columns[9]) {
                    case "NO_ACTION" -> noActionIds.add(columns[10]);
                    case "REVIEW_REQUIRED" -> reviewRequiredIds.add(columns[10]);
                    case "EXCLUDED_NO_FINDING" -> negativeControlIds.add(columns[10]);
                    default -> throw new IllegalArgumentException("Unknown SOY golden outcome: " + columns[9]);
                }
            }
            assertThat(metadata).isNotNull();
            return new GoldenScenario(
                    metadata[0],
                    Integer.parseInt(metadata[1]),
                    metadata[2],
                    metadata[3],
                    metadata[4],
                    metadata[5],
                    metadata[6],
                    metadata[7],
                    Boolean.parseBoolean(metadata[8]),
                    noActionIds,
                    reviewRequiredIds,
                    negativeControlIds
            );
        }
    }

    private static void assertCurrentReleasedFormulaAndPublishedLabel(
            RelevantProduct product,
            String baselineSpecificationId
    ) {
        assertThat(product.formulaVersionId()).startsWith("formula_");
        assertThat(product.formulaStatus()).isEqualTo("RELEASED");
        assertThat(product.currentReleased()).isEqualTo("Y");
        assertThat(product.specificationVersionId()).isEqualTo(baselineSpecificationId);
        assertThat(product.labelVersionId()).startsWith("label_");
        assertThat(product.labelStatus()).isEqualTo("PUBLISHED");
        assertThat(product.currentPublished()).isEqualTo("Y");
    }

    private record RelevantProduct(
            String productId,
            String formulaVersionId,
            String formulaStatus,
            String currentReleased,
            String specificationVersionId,
            String labelVersionId,
            String labelStatus,
            String currentPublished,
            boolean formulaHasSoy,
            boolean labelDeclaresSoy
    ) {
    }

    private record NegativeControl(
            String productId,
            String fixtureGroup,
            String formulaVersionId,
            String formulaStatus,
            String currentReleased,
            int changedMaterialItems
    ) {
    }

    private record GoldenScenario(
            String goldenId,
            int version,
            String status,
            String changedSupplierMaterialId,
            String baselineSpecificationVersionId,
            String addedIngredientId,
            String addedIngredientName,
            String candidateSpecificationVersionId,
            boolean syntheticMappingUsed,
            Set<String> noActionIds,
            Set<String> reviewRequiredIds,
            Set<String> negativeControlIds
    ) {
    }
}
