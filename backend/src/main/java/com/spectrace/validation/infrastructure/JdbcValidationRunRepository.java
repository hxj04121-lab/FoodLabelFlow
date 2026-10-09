package com.spectrace.validation.infrastructure;

import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcValidationRunRepository implements ValidationRunRepository {

    private static final String INSERT = "CALL sp_insert_validation_run(?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT = """
            SELECT validation_run_id, label_version_id, rule_set_version_id, status,
                   ran_by_user_id, ran_at, summary, data_provenance_id
            FROM validation_run
            WHERE validation_run_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcValidationRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(ValidationRun validationRun) {
        // The V10 routine inserts a fresh identity and its current association;
        // it never commits independently of the surrounding results/audit flow.
        jdbcTemplate.update(
                INSERT,
                validationRun.validationRunId(),
                validationRun.labelVersionId(),
                validationRun.ruleSetVersionId(),
                validationRun.status().name(),
                validationRun.ranByUserId(),
                JdbcValueMapping.writeUtcDateTime(validationRun.ranAt()),
                validationRun.summary(),
                validationRun.dataProvenanceId()
        );
    }

    @Override
    public Optional<ValidationRun> findById(String validationRunId) {
        List<ValidationRun> rows = jdbcTemplate.query(SELECT, this::mapValidationRun, validationRunId);
        return rows.stream().findFirst();
    }

    private ValidationRun mapValidationRun(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ValidationRun(
                resultSet.getString("validation_run_id"),
                resultSet.getString("label_version_id"),
                resultSet.getString("rule_set_version_id"),
                ValidationStatus.fromDatabase(resultSet.getString("status")),
                resultSet.getString("ran_by_user_id"),
                JdbcValueMapping.readUtcDateTime(resultSet, "ran_at"),
                resultSet.getString("summary"),
                resultSet.getString("data_provenance_id")
        );
    }
}
