package com.spectrace.catalog.infrastructure;

import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Catalog-owned specification reads behind the change-request port; callers never join these tables. */
@Repository
public class JdbcSpecificationVersionLookupAdapter implements SpecificationVersionLookupPort {
    private static final String COLUMNS = """
            SELECT specification_version_id, supplier_material_id, version_number,
                   lifecycle_status, effective_date
            FROM ingredient_specification_version
            """;
    private static final String SELECT = COLUMNS + "WHERE specification_version_id = ?\n";

    private final JdbcTemplate jdbc;

    public JdbcSpecificationVersionLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supplierMaterialExists(String supplierMaterialId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM supplier_material WHERE supplier_material_id = ?",
                Integer.class, requiredText(supplierMaterialId, "supplierMaterialId"));
        return count != null && count > 0;
    }

    @Override
    public Optional<SpecificationVersionFacts> findById(String specificationVersionId) {
        return query(SELECT, specificationVersionId);
    }

    @Override
    public Optional<SpecificationVersionFacts> lockById(String specificationVersionId) {
        return query(SELECT + "FOR UPDATE", specificationVersionId);
    }

    @Override
    public List<SpecificationVersionFacts> findAllById(Collection<String> specificationVersionIds) {
        List<String> ids = specificationVersionIds.stream()
                .map(id -> requiredText(id, "specificationVersionId")).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(COLUMNS + "WHERE specification_version_id IN ("
                        + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")",
                (row, index) -> map(row), ids.toArray());
    }

    private Optional<SpecificationVersionFacts> query(String sql, String specificationVersionId) {
        return jdbc.query(sql, (row, index) -> map(row),
                        requiredText(specificationVersionId, "specificationVersionId"))
                .stream().findFirst();
    }

    private static SpecificationVersionFacts map(ResultSet row) throws SQLException {
        String lifecycle = row.getString("lifecycle_status");
        if (lifecycle == null) {
            throw new IllegalStateException("specification lifecycle is missing");
        }
        Lifecycle parsed;
        try {
            parsed = Lifecycle.valueOf(lifecycle);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unsupported specification lifecycle: " + lifecycle, error);
        }
        return new SpecificationVersionFacts(
                row.getString("specification_version_id"),
                row.getString("supplier_material_id"),
                row.getInt("version_number"),
                parsed,
                row.getDate("effective_date").toLocalDate());
    }
}
