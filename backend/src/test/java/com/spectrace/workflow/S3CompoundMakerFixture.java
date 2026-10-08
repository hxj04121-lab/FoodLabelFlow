package com.spectrace.workflow;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only subject, installed solely in each caller's owned MySQL Testcontainer. */
final class S3CompoundMakerFixture {
    static final String USER_ID = "user_test_compound_maker_s3";
    static final String SUBJECT = "dev-external-test-compound-maker-s3";
    static final String CASE_ALIAS_SUBJECT = "DEV-EXTERNAL-TEST-COMPOUND-MAKER-S3";
    static final String POLICY_MESSAGE = "Maker-checker violation: creator cannot approve own label";
    static final List<String> IDENTITY_TABLES = List.of(
            "user_account", "user_role", "role", "role_permission", "permission");
    static final List<String> BUSINESS_TABLES = List.of(
            "product", "formula_version", "formula_item", "label_version", "label_allergen_declaration",
            "validation_run", "validation_result", "change_request",
            "impact_analysis_run", "impact_finding", "review_task", "approval_record",
            "publication_record", "audit_event");

    private S3CompoundMakerFixture() { }

    static void install(JdbcTemplate jdbc) {
        var originals = originalIdentityRows(jdbc);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE user_id=?", Integer.class,
                USER_ID)).as("fixture must be a new actor in an isolated database").isZero();
        new ResourceDatabasePopulator(new ClassPathResource("fixtures/s3-compound-maker.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        assertThat(originalIdentityRows(jdbc)).as("all existing identity and permission rows stay unchanged")
                .isEqualTo(originals);
        assertThat(jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=? ORDER BY role_id",
                String.class, USER_ID)).containsExactly("role_approver", "role_label_officer");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE auth_provider='DEV_EXTERNAL' "
                + "AND external_auth_subject=? AND is_active='Y'", Integer.class, CASE_ALIAS_SUBJECT))
                .as("ASCII subject case alias resolves through the actual existing database collation").isEqualTo(1);
    }

    static Map<String, List<Map<String, Object>>> allRows(JdbcTemplate jdbc) {
        var tables = new java.util.ArrayList<>(BUSINESS_TABLES);
        tables.addAll(IDENTITY_TABLES);
        return rows(jdbc, tables);
    }

    static Map<String, List<Map<String, Object>>> rows(JdbcTemplate jdbc, List<String> tables) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : tables) result.put(table, jdbc.queryForList("SELECT * FROM `" + table + "` ORDER BY 1,2"));
        return result;
    }

    private static Map<String, List<Map<String, Object>>> originalIdentityRows(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : IDENTITY_TABLES) {
            String filter = table.equals("user_account") || table.equals("user_role") ? " WHERE user_id<>?" : "";
            String sql = "SELECT * FROM `" + table + "`" + filter + " ORDER BY 1,2";
            result.put(table, filter.isEmpty() ? jdbc.queryForList(sql) : jdbc.queryForList(sql, USER_ID));
        }
        return result;
    }
}
