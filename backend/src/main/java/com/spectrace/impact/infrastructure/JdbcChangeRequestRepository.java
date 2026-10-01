package com.spectrace.impact.infrastructure;

import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/** Maps VersionChange onto the one typed column pair that chk_change_request_typed_refs_v3 allows. */
@Repository
public class JdbcChangeRequestRepository implements ChangeRequestRepository {

    private static final String COLUMNS = """
            change_request_id, change_request_code, change_type, status, requested_at,
            requested_by_user_id, description,
            from_specification_version_id, to_specification_version_id,
            from_formula_version_id, to_formula_version_id,
            from_rule_set_version_id, to_rule_set_version_id, data_provenance_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcChangeRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ChangeRequest request) {
        VersionChange change = request.versionChange();
        ChangeType type = request.changeType();
        int inserted = jdbc.update(
                "INSERT INTO change_request(" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                request.changeRequestId(),
                request.changeRequestCode(),
                type.name(),
                request.status().name(),
                LocalDateTime.ofInstant(request.requestedAt(), ZoneOffset.UTC),
                request.requestedByUserId(),
                request.description(),
                typed(type, ChangeType.INGREDIENT_SPEC, change.fromVersionId()),
                typed(type, ChangeType.INGREDIENT_SPEC, change.toVersionId()),
                typed(type, ChangeType.FORMULA, change.fromVersionId()),
                typed(type, ChangeType.FORMULA, change.toVersionId()),
                typed(type, ChangeType.RULE_SET, change.fromVersionId()),
                typed(type, ChangeType.RULE_SET, change.toVersionId()),
                request.dataProvenanceId());
        if (inserted != 1) {
            throw new IllegalStateException("Expected one change request row to be inserted");
        }
    }

    @Override
    public Optional<ChangeRequest> findById(String changeRequestId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM change_request WHERE change_request_id = ?",
                        (row, index) -> map(row), changeRequestId)
                .stream().findFirst();
    }

    @Override
    public Optional<ChangeRequest> findOpenByVersionChange(ChangeType changeType, VersionChange versionChange) {
        String prefix = columnPrefix(Objects.requireNonNull(changeType, "changeType"));
        // A locking read sees rows committed after this transaction's snapshot was taken,
        // so a request that waited on the target-version lock observes the winner's insert.
        return jdbc.query("SELECT " + COLUMNS + " FROM change_request"
                                + " WHERE change_type = ? AND from_" + prefix + "_version_id = ?"
                                + " AND to_" + prefix + "_version_id = ? AND status <> 'CANCELLED'"
                                + " ORDER BY requested_at, change_request_id LIMIT 1 FOR SHARE",
                        (row, index) -> map(row),
                        changeType.name(), versionChange.fromVersionId(), versionChange.toVersionId())
                .stream().findFirst();
    }

    private static ChangeRequest map(ResultSet row) throws SQLException {
        ChangeType type = ChangeType.fromDatabase(row.getString("change_type"));
        String prefix = columnPrefix(type);
        return new ChangeRequest(
                row.getString("change_request_id"),
                row.getString("change_request_code"),
                type,
                ChangeRequestStatus.fromDatabase(row.getString("status")),
                row.getObject("requested_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                row.getString("requested_by_user_id"),
                row.getString("description"),
                new VersionChange(
                        row.getString("from_" + prefix + "_version_id"),
                        row.getString("to_" + prefix + "_version_id")),
                row.getString("data_provenance_id"));
    }

    private static String typed(ChangeType actual, ChangeType column, String value) {
        return actual == column ? value : null;
    }

    private static String columnPrefix(ChangeType type) {
        return switch (type) {
            case INGREDIENT_SPEC -> "specification";
            case FORMULA -> "formula";
            case RULE_SET -> "rule_set";
        };
    }
}
