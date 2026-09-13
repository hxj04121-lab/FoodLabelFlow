package com.spectrace.validation.infrastructure;

import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationSeverity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@Repository
public class JdbcValidationResultRepository implements ValidationResultRepository {

    private static final String INSERT = """
            INSERT INTO validation_result(
              validation_result_id, validation_run_id, rule_definition_id,
              result_code, severity, passed, blocking, message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT = """
            SELECT validation_result_id, validation_run_id, rule_definition_id,
                   result_code, severity, passed, blocking, message
            FROM validation_result
            WHERE validation_run_id = ?
            ORDER BY validation_result_id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcValidationResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(String validationRunId, List<ValidationResult> results) {
        List<ValidationResult> values = results == null ? List.of() : List.copyOf(results);
        values.forEach(result -> {
            if (!validationRunId.equals(result.validationRunId())) {
                throw new IllegalArgumentException("All results must belong to the supplied validation run");
            }
        });
        if (values.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                ValidationResult result = values.get(index);
                statement.setString(1, result.validationResultId());
                statement.setString(2, result.validationRunId());
                if (result.ruleDefinitionId() == null) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, result.ruleDefinitionId());
                }
                statement.setString(4, result.resultCode());
                statement.setString(5, result.severity().name());
                statement.setString(6, JdbcValueMapping.writeFlag(result.passed()));
                statement.setString(7, JdbcValueMapping.writeFlag(result.blocking()));
                statement.setString(8, result.message());
            }

            @Override
            public int getBatchSize() {
                return values.size();
            }
        });
    }

    @Override
    public List<ValidationResult> findByRunId(String validationRunId) {
        return jdbcTemplate.query(SELECT, this::mapValidationResult, validationRunId);
    }

    private ValidationResult mapValidationResult(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ValidationResult(
                resultSet.getString("validation_result_id"),
                resultSet.getString("validation_run_id"),
                resultSet.getString("rule_definition_id"),
                resultSet.getString("result_code"),
                ValidationSeverity.fromDatabase(resultSet.getString("severity")),
                JdbcValueMapping.readFlag(resultSet, "passed"),
                JdbcValueMapping.readFlag(resultSet, "blocking"),
                resultSet.getString("message")
        );
    }
}
