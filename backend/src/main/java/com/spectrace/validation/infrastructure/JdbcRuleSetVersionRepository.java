package com.spectrace.validation.infrastructure;

import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationSeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRuleSetVersionRepository implements RuleSetVersionRepository {

    private static final String RULE_SET_COLUMNS = """
            SELECT rule_set_version_id, rule_set_code, version_number, jurisdiction_code,
                   lifecycle_status, effective_from, effective_to, is_demo_only,
                   description, data_provenance_id
            FROM rule_set_version
            WHERE rule_set_version_id = ?
            """;

    private static final String ACTIVE_RULE_SET_COLUMNS = RULE_SET_COLUMNS + " AND lifecycle_status = 'ACTIVE'";

    private static final String RULE_DEFINITION_COLUMNS = """
            SELECT rule_definition_id, rule_set_version_id, rule_code, rule_type,
                   target_allergen_id, pattern_text, severity, is_active, description
            FROM rule_definition
            WHERE rule_set_version_id = ?
            ORDER BY rule_definition_id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuleSetVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RuleSetVersion> findById(String ruleSetVersionId) {
        return findOne(RULE_SET_COLUMNS, ruleSetVersionId);
    }

    @Override
    public Optional<RuleSetVersion> findActiveById(String ruleSetVersionId) {
        return findOne(ACTIVE_RULE_SET_COLUMNS, ruleSetVersionId);
    }

    private Optional<RuleSetVersion> findOne(String query, String ruleSetVersionId) {
        List<RuleSetVersion> rows = jdbcTemplate.query(query, this::mapRuleSetVersion, ruleSetVersionId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        RuleSetVersion ruleSetVersion = rows.getFirst();
        List<RuleDefinition> ruleDefinitions = jdbcTemplate.query(
                RULE_DEFINITION_COLUMNS,
                this::mapRuleDefinition,
                ruleSetVersion.ruleSetVersionId()
        );
        return Optional.of(new RuleSetVersion(
                ruleSetVersion.ruleSetVersionId(),
                ruleSetVersion.ruleSetCode(),
                ruleSetVersion.versionNumber(),
                ruleSetVersion.jurisdictionCode(),
                ruleSetVersion.lifecycleStatus(),
                ruleSetVersion.effectiveFrom(),
                ruleSetVersion.effectiveTo(),
                ruleSetVersion.demoOnly(),
                ruleSetVersion.description(),
                ruleSetVersion.dataProvenanceId(),
                ruleDefinitions
        ));
    }

    private RuleSetVersion mapRuleSetVersion(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new RuleSetVersion(
                resultSet.getString("rule_set_version_id"),
                resultSet.getString("rule_set_code"),
                resultSet.getString("version_number"),
                resultSet.getString("jurisdiction_code"),
                RuleSetLifecycleStatus.fromDatabase(resultSet.getString("lifecycle_status")),
                JdbcValueMapping.readDate(resultSet, "effective_from"),
                JdbcValueMapping.readDate(resultSet, "effective_to"),
                JdbcValueMapping.readFlag(resultSet, "is_demo_only"),
                resultSet.getString("description"),
                resultSet.getString("data_provenance_id"),
                List.of()
        );
    }

    private RuleDefinition mapRuleDefinition(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new RuleDefinition(
                resultSet.getString("rule_definition_id"),
                resultSet.getString("rule_set_version_id"),
                resultSet.getString("rule_code"),
                RuleType.fromDatabase(resultSet.getString("rule_type")),
                resultSet.getString("target_allergen_id"),
                resultSet.getString("pattern_text"),
                ValidationSeverity.fromDatabase(resultSet.getString("severity")),
                JdbcValueMapping.readFlag(resultSet, "is_active"),
                resultSet.getString("description")
        );
    }
}
