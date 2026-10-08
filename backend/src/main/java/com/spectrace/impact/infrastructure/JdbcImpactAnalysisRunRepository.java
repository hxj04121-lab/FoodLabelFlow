package com.spectrace.impact.infrastructure;

import com.spectrace.impact.application.ImpactRunAlreadyExistsException;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactRunStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcImpactAnalysisRunRepository implements ImpactAnalysisRunRepository {

    private static final String INSERT = """
            INSERT INTO impact_analysis_run(
              impact_analysis_run_id, run_code, change_request_id, idempotency_key,
              rule_set_version_id, status, started_at, completed_at,
              executed_by_user_id, data_provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT = """
            SELECT impact_analysis_run_id, run_code, change_request_id,
                   rule_set_version_id, status, started_at, completed_at,
                   executed_by_user_id, data_provenance_id
            FROM impact_analysis_run
            WHERE %s = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcImpactAnalysisRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ImpactAnalysisRun run) {
        int updated;
        try {
            updated = jdbcTemplate.update(
                    INSERT,
                    run.impactAnalysisRunId(),
                    run.runCode(),
                    run.changeRequestId(),
                    idempotencyKey(run),
                    run.ruleSetVersionId(),
                    run.status().name(),
                    ImpactJdbcValueMapping.writeUtcDateTime(run.startedAt()),
                    run.completedAt() == null ? null : ImpactJdbcValueMapping.writeUtcDateTime(run.completedAt()),
                    run.executedByUserId(),
                    run.dataProvenanceId()
            );
        } catch (DuplicateKeyException duplicate) {
            // An insert waits for a concurrent winner to commit. Use a current locking
            // read here so a previously established REPEATABLE READ snapshot cannot hide it.
            List<ImpactAnalysisRun> existing = jdbcTemplate.query(
                    SELECT.formatted("change_request_id") + " FOR SHARE",
                    this::mapImpactAnalysisRun, run.changeRequestId());
            if (!existing.isEmpty()) {
                throw new ImpactRunAlreadyExistsException(existing.getFirst(), duplicate);
            }
            // A collision on another run's ID/code is not an idempotent replay.
            throw duplicate;
        }
        if (updated != 1) {
            throw new IllegalStateException("Expected one impact analysis run row to be inserted");
        }
    }

    @Override
    public Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId) {
        return find("impact_analysis_run_id", impactAnalysisRunId);
    }

    @Override
    public List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId) {
        return jdbcTemplate.query(
                SELECT.formatted("change_request_id")
                        + " ORDER BY started_at ASC, impact_analysis_run_id ASC",
                this::mapImpactAnalysisRun,
                changeRequestId
        );
    }

    @Override
    public List<ImpactAnalysisRun> findByChangeRequestIdForReplay(String changeRequestId) {
        return jdbcTemplate.query(
                SELECT.formatted("change_request_id")
                        + " ORDER BY started_at ASC, impact_analysis_run_id ASC FOR SHARE",
                this::mapImpactAnalysisRun,
                changeRequestId
        );
    }

    private Optional<ImpactAnalysisRun> find(String column, String value) {
        List<ImpactAnalysisRun> rows = jdbcTemplate.query(
                SELECT.formatted(column),
                this::mapImpactAnalysisRun,
                value
        );
        return rows.stream().findFirst();
    }

    private ImpactAnalysisRun mapImpactAnalysisRun(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ImpactAnalysisRun(
                resultSet.getString("impact_analysis_run_id"),
                resultSet.getString("run_code"),
                resultSet.getString("change_request_id"),
                resultSet.getString("rule_set_version_id"),
                ImpactRunStatus.fromDatabase(resultSet.getString("status")),
                ImpactJdbcValueMapping.readUtcDateTime(resultSet, "started_at"),
                ImpactJdbcValueMapping.readUtcDateTime(resultSet, "completed_at"),
                resultSet.getString("executed_by_user_id"),
                resultSet.getString("data_provenance_id")
        );
    }

    private static String idempotencyKey(ImpactAnalysisRun run) {
        return "impact-analysis:" + run.changeRequestId();
    }
}
