package com.spectrace.impact.infrastructure;

import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@Repository
public class JdbcImpactFindingRepository implements ImpactFindingRepository {

    private static final String INSERT = """
            INSERT INTO impact_finding(
              impact_finding_id, impact_analysis_run_id, product_id,
              current_formula_version_id, proposed_formula_version_id,
              current_label_version_id, classification, missing_allergen_codes,
              explanation, data_provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT = """
            SELECT impact_finding_id, impact_analysis_run_id, product_id,
                   current_formula_version_id, proposed_formula_version_id,
                   current_label_version_id, classification, missing_allergen_codes,
                   explanation, data_provenance_id
            FROM impact_finding
            WHERE impact_analysis_run_id = ?
            ORDER BY product_id ASC, impact_finding_id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcImpactFindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(String impactAnalysisRunId, List<ImpactFinding> findings) {
        if (impactAnalysisRunId == null || impactAnalysisRunId.isBlank()) {
            throw new IllegalArgumentException("impactAnalysisRunId is required");
        }
        List<ImpactFinding> values = findings == null ? List.of() : List.copyOf(findings);
        values.forEach(finding -> {
            if (!impactAnalysisRunId.equals(finding.impactAnalysisRunId())) {
                throw new IllegalArgumentException("All findings must belong to the supplied impact analysis run");
            }
        });
        if (values.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                ImpactFinding finding = values.get(index);
                statement.setString(1, finding.impactFindingId());
                statement.setString(2, finding.impactAnalysisRunId());
                statement.setString(3, finding.productId());
                statement.setString(4, finding.currentFormulaVersionId());
                if (finding.proposedFormulaVersionId() == null) {
                    statement.setNull(5, Types.VARCHAR);
                } else {
                    statement.setString(5, finding.proposedFormulaVersionId());
                }
                statement.setString(6, finding.currentLabelVersionId());
                statement.setString(7, finding.classification().name());
                statement.setString(8, ImpactJsonMapping.writeStrings(finding.missingAllergenCodes()));
                statement.setString(9, finding.explanation());
                statement.setString(10, finding.dataProvenanceId());
            }

            @Override
            public int getBatchSize() {
                return values.size();
            }
        });
    }

    @Override
    public List<ImpactFinding> findByRunId(String impactAnalysisRunId) {
        return jdbcTemplate.query(SELECT, this::mapImpactFinding, impactAnalysisRunId);
    }

    @Override
    public List<ImpactFinding> findByRunIdForReplay(String impactAnalysisRunId) {
        return jdbcTemplate.query(SELECT + " FOR SHARE", this::mapImpactFinding, impactAnalysisRunId);
    }

    private ImpactFinding mapImpactFinding(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ImpactFinding(
                resultSet.getString("impact_finding_id"),
                resultSet.getString("impact_analysis_run_id"),
                resultSet.getString("product_id"),
                resultSet.getString("current_formula_version_id"),
                resultSet.getString("proposed_formula_version_id"),
                resultSet.getString("current_label_version_id"),
                ImpactClassification.fromDatabase(resultSet.getString("classification")),
                ImpactJsonMapping.readStrings(resultSet.getString("missing_allergen_codes")),
                resultSet.getString("explanation"),
                resultSet.getString("data_provenance_id")
        );
    }
}
