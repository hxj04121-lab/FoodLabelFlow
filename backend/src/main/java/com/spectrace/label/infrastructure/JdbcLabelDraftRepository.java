package com.spectrace.label.infrastructure;

import com.spectrace.label.application.port.LabelDraftRepository;
import com.spectrace.label.application.LabelDeclarationInput;
import com.spectrace.label.application.InvalidLabelDraftRequestException;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.label.domain.LabelDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Repository
public class JdbcLabelDraftRepository implements LabelDraftRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AllergenFactsPort allergens;

    public JdbcLabelDraftRepository(JdbcTemplate jdbcTemplate, AllergenFactsPort allergens) {
        this.jdbcTemplate = jdbcTemplate;
        this.allergens = allergens;
    }

    @Override
    @Transactional
    public LabelDraft createFromCurrentFormula(
            String productId,
            String jurisdictionCode,
            String actorUserId,
            List<LabelDeclarationInput> declarations
    ) {
        CurrentProduct current = jdbcTemplate.query(
                """
                SELECT
                    p.current_formula_version_id,
                    p.source_ingredients_text,
                    p.data_provenance_id,
                    fv.lifecycle_status,
                    fv.is_current_released
              FROM product p
          JOIN formula_version fv
           ON fv.formula_version_id = p.current_formula_version_id
          WHERE p.product_id = ?
          FOR UPDATE
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return new CurrentProduct(
                            rs.getString("current_formula_version_id"),
                            rs.getString("source_ingredients_text"),
                            rs.getString("data_provenance_id"),
                            rs.getString("lifecycle_status"),
                            rs.getString("is_current_released")
                    );
                },
                productId
        );

        if (current == null) {
            throw new IllegalArgumentException(
                    "Unknown product or current formula: " + productId
            );
        }

        if (!"RELEASED".equals(current.lifecycleStatus())
                || !"Y".equals(current.isCurrentReleased())) {
            throw new IllegalStateException(
                    "Product does not have a current released formula"
            );
        }

        Set<String> catalogIds = allergens.listAllergens(jurisdictionCode).stream()
                .map(entry -> entry.allergenId()).collect(Collectors.toSet());
        for (var row : declarations) {
            if (!catalogIds.contains(row.allergenId())) {
                throw new InvalidLabelDraftRequestException("Allergen does not belong to the jurisdiction catalog: " + row.allergenId());
            }
        }

        String ruleSetVersionId = jdbcTemplate.query(
                """
                SELECT rule_set_version_id
                FROM rule_set_version
                WHERE jurisdiction_code = ?
                  AND lifecycle_status = 'ACTIVE'
                  AND effective_from <= CURRENT_DATE
                  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                ORDER BY version_number DESC
                LIMIT 1
                """,
                rs -> rs.next()
                        ? rs.getString("rule_set_version_id")
                        : null,
                jurisdictionCode
        );

        if (ruleSetVersionId == null) {
            throw new IllegalStateException(
                    "No active rule set for jurisdiction: "
                            + jurisdictionCode
            );
        }

        Integer versionNumber = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(version_number), 0) + 1
                FROM label_version
                WHERE product_id = ?
                  AND jurisdiction_code = ?
                """,
                Integer.class,
                productId,
                jurisdictionCode
        );

        String labelVersionId =
                "label_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        LocalDateTime createdAt = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO label_version (
                    label_version_id,
                    product_id,
                    formula_version_id,
                    rule_set_version_id,
                    jurisdiction_code,
                    version_number,
                    raw_ingredient_text,
                    lifecycle_status,
                    is_current_published,
                    created_by_user_id,
                    created_at,
                    data_provenance_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'N', ?, ?, ?)
                """,
                labelVersionId,
                productId,
                current.formulaVersionId(),
                ruleSetVersionId,
                jurisdictionCode,
                versionNumber,
                current.sourceIngredientsText(),
                actorUserId,
                Timestamp.valueOf(createdAt),
                current.dataProvenanceId()
        );

        for (var row : declarations) {
            jdbcTemplate.update("""
                    INSERT INTO label_allergen_declaration (
                        label_allergen_declaration_id, label_version_id, allergen_id,
                        declaration_type, declaration_source, display_text, data_provenance_id
                    ) VALUES (?, ?, ?, 'CONTAINS', 'USER_ENTERED', ?, ?)
                    """, "lad_" + UUID.randomUUID().toString().replace("-", ""),
                    labelVersionId, row.allergenId(), row.displayText(), current.dataProvenanceId());
        }

        return findById(labelVersionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created label draft could not be read"
                ));
    }

    @Override
    public Optional<LabelDraft> findById(String labelVersionId) {
        return jdbcTemplate.query(
                """
                SELECT
                    label_version_id,
                    product_id,
                    formula_version_id,
                    rule_set_version_id,
                    jurisdiction_code,
                    version_number,
                    raw_ingredient_text,
                    lifecycle_status,
                    is_current_published,
                    created_by_user_id,
                    created_at,
                    data_provenance_id
                FROM label_version
                WHERE label_version_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new LabelDraft(
                            rs.getString("label_version_id"),
                            rs.getString("product_id"),
                            rs.getString("formula_version_id"),
                            rs.getString("rule_set_version_id"),
                            rs.getString("jurisdiction_code"),
                            rs.getInt("version_number"),
                            rs.getString("raw_ingredient_text"),
                            rs.getString("lifecycle_status"),
                            rs.getString("is_current_published"),
                            rs.getString("created_by_user_id"),
                            rs.getTimestamp("created_at")
                                    .toLocalDateTime(),
                            rs.getString("data_provenance_id")
                    ));
                },
                labelVersionId
        );
    }

    private record CurrentProduct(
            String formulaVersionId,
            String sourceIngredientsText,
            String dataProvenanceId,
            String lifecycleStatus,
            String isCurrentReleased
    ) {
    }
}
