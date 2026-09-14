package com.spectrace.allergen.infrastructure;

import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Owner-side adapter for canonical allergen facts. */
@Repository
public class JdbcAllergenFactsAdapter implements AllergenFactsPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAllergenFactsAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AllergenFact> findByIds(Collection<String> allergenIds) {
        List<String> ids = allergenIds == null ? List.of() : allergenIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
                """
                SELECT allergen_id, allergen_code, display_name, jurisdiction_code
                FROM allergen
                WHERE allergen_id IN (""" + placeholders + ") ORDER BY allergen_id",
                (rs, rowNum) -> new AllergenFact(
                        rs.getString("allergen_id"),
                        rs.getString("allergen_code"),
                        rs.getString("display_name"),
                        rs.getString("jurisdiction_code")
                ),
                ids.toArray()
        );
    }
}
