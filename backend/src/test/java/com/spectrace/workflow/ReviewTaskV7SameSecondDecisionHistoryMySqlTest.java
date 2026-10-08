package com.spectrace.workflow;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.LabelReviewService;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import com.spectrace.workflow.infrastructure.JdbcLabelReviewCommandRepository;
import org.assertj.core.api.SoftAssertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Controlled same-second decision history regression: the valid final APPROVE state must remain
 * publishable after V7 and its forward repair. This uses fresh containers, not a business database.
 */
@Testcontainers
class ReviewTaskV7SameSecondDecisionHistoryMySqlTest {

    private static final String LABEL_ID = "label_pr67_v6_upgrade";
    private static final String TASK_ID = "review_pr67_v6_upgrade";
    private static final String PRODUCT_ID = "prod_usda_1106285";
    private static final String OLD_LABEL_ID = "label_1106285_v1";

    @Container
    private final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("spectrace");

    @Test
    void sameSecondReviewHistoryRemainsPublishableAfterV7() {
        verifySameSecondHistory(false);
    }

    @Test
    void repairsDatabaseThatAlreadyAppliedV7WithoutChangingItsChecksum() {
        verifySameSecondHistory(true);
    }

    private void verifySameSecondHistory(boolean alreadyAppliedV7) {
        System.out.println("MIGRATION_TEST_CONTAINER_ID=" + MYSQL.getContainerId());
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        assertThat(jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1
                """, String.class)).isEqualTo("6");

        insertMinimumLegacyPrerequisites(jdbc);
        // The actual V6 procedures establish lifecycle, task and approval record.
        jdbc.update("CALL sp_submit_label_for_review(?, ?)", LABEL_ID, "user_label_officer");
        jdbc.update("CALL sp_record_label_decision(?, ?, ?, ?)",
                LABEL_ID, "user_approver", "REQUEST_CHANGES", "V6 first decision requests changes");
        jdbc.update("CALL sp_submit_label_for_review(?, ?)", LABEL_ID, "user_label_officer");
        jdbc.update("CALL sp_record_label_decision(?, ?, ?, ?)",
                LABEL_ID, "user_approver", "APPROVE", "V6 final decision approves revision");

        // Controlled historical input: old Java used NOW() at DATETIME second precision
        // and random UUID approval IDs. Both decisions can share a timestamp while
        // the earlier REQUEST_CHANGES randomly sorts after the final APPROVE.
        // Preserve the genuine procedures' decision/lifecycle transitions; model
        // only the persisted time and UUID ordering that the old Java writer permits.
        assertThat(jdbc.update("""
                UPDATE approval_record
                SET approval_record_id = 'approval_ffffffffffff4fffafffffffffffffff',
                    decided_at = '2026-10-07 12:00:00'
                WHERE review_task_id = ? AND label_version_id = ? AND decision = 'REQUEST_CHANGES'
                """, TASK_ID, LABEL_ID)).isEqualTo(1);
        assertThat(jdbc.update("""
                UPDATE approval_record
                SET approval_record_id = 'approval_00000000000040008000000000000000',
                    decided_at = '2026-10-07 12:00:00'
                WHERE review_task_id = ? AND label_version_id = ? AND decision = 'APPROVE'
                """, TASK_ID, LABEL_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_record WHERE review_task_id = ? AND label_version_id = ?
                """, Integer.class, TASK_ID, LABEL_ID)).isEqualTo(2);
        System.out.println("CONTROLLED_V6_HISTORY: REQUEST_CHANGES then APPROVE; same second; earlier random UUID sorts larger");

        assertThat(labelStatus(jdbc)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM review_task WHERE review_task_id = ?", String.class, TASK_ID))
                .isEqualTo("CLOSED");
        var approvalBeforeUpgrade = jdbc.queryForMap("""
                SELECT * FROM approval_record WHERE review_task_id = ? AND label_version_id = ? AND decision = 'APPROVE'
                """, TASK_ID, LABEL_ID);
        assertThat(approvalBeforeUpgrade)
                .containsEntry("decision", "APPROVE")
                .containsEntry("decided_by_user_id", "user_approver");
        assertThat(publicationCount(jdbc)).isZero();
        var oldPublishedLabel = jdbc.queryForMap(
                "SELECT * FROM label_version WHERE label_version_id = ?", OLD_LABEL_ID);
        var oldPointer = jdbc.queryForObject("""
                SELECT current_published_label_version_id FROM product WHERE product_id = ?
                """, String.class, PRODUCT_ID);
        System.out.println("LEGACY_V6_APPROVAL_ESTABLISHED: label=APPROVED task=CLOSED approve_records=1");

        var immutableBeforeUpgrade = immutableHistorySnapshot(jdbc);
        Integer v7Checksum = null;
        if (alreadyAppliedV7) {
            Flyway.configure().dataSource(dataSource).target("7").load().migrate();
            assertThat(jdbc.queryForMap("SELECT status, decision FROM review_task WHERE review_task_id = ?", TASK_ID))
                    .containsEntry("status", "CLOSED").containsEntry("decision", "REQUEST_CHANGES");
            v7Checksum = jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '7'", Integer.class);
            System.out.println("PRE_APPLIED_V7: original wrong CLOSED/REQUEST_CHANGES verified; checksum=" + v7Checksum);
        }
        var upgraded = Flyway.configure().dataSource(dataSource).load();
        upgraded.migrate();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1
                """, String.class)).isEqualTo("8");
        assertThat(immutableHistorySnapshot(jdbc)).isEqualTo(immutableBeforeUpgrade);
        if (alreadyAppliedV7) {
            assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '7'", Integer.class))
                    .isEqualTo(v7Checksum);
        }
        assertThat(jdbc.queryForMap("""
                SELECT * FROM approval_record WHERE review_task_id = ? AND label_version_id = ? AND decision = 'APPROVE'
                """, TASK_ID, LABEL_ID)).isEqualTo(approvalBeforeUpgrade);

        var migratedTask = jdbc.queryForMap("SELECT * FROM review_task WHERE review_task_id = ?", TASK_ID);
        System.out.println("AFTER_V8: target=" + migratedTask.get("target_label_version_id")
                + " status=" + migratedTask.get("status") + " decision=" + migratedTask.get("decision"));
        var softly = new SoftAssertions();
        softly.assertThat(migratedTask.get("draft_label_version_id"))
                .as("V7 preserves the legacy draft reference").isEqualTo(LABEL_ID);
        softly.assertThat(migratedTask.get("target_label_version_id"))
                .as("V7 backfills the publication target").isEqualTo(LABEL_ID);
        softly.assertThat(migratedTask.get("status"))
                .as("an approved but unpublished legacy task remains unresolved under V7")
                .isEqualTo("IN_REVIEW");
        softly.assertThat(migratedTask.get("decision"))
                .as("V7 preserves the genuine final approval for publication").isEqualTo("APPROVE");
        softly.assertThat(migratedTask.get("resolved_at"))
                .as("approval alone is not publication resolution").isNull();

        var service = new LabelReviewService(new AuthorizationService(),
                new JdbcLabelReviewCommandRepository(jdbc), new LabelTransitionPolicy(), new MakerCheckerPolicy());
        var publisher = new AuthenticatedActor("user_publisher", "publication.manager", "Demo Publisher",
                Set.of("PUBLISHER"), Set.of("LABEL.PUBLISH"));
        // Explicitly provide the same JDBC transaction boundary as the Spring service proxy.
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Throwable publicationFailure = catchThrowable(() -> transaction.executeWithoutResult(
                ignored -> service.publishReviewTask(TASK_ID, LABEL_ID, publisher)));
        softly.assertThat(publicationFailure)
                .as("new Java publication must accept a genuine pre-upgrade APPROVE/CLOSED task")
                .isNull();

        if (publicationFailure == null) {
            assertThat(labelStatus(jdbc)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("""
                    SELECT current_published_label_version_id FROM product WHERE product_id = ?
                    """, String.class, PRODUCT_ID)).isEqualTo(LABEL_ID);
            assertThat(publicationCount(jdbc)).isEqualTo(1);
            assertThat(publicationAuditCount(jdbc)).isEqualTo(1);
            var resolved = jdbc.queryForMap("SELECT * FROM review_task WHERE review_task_id = ?", TASK_ID);
            assertThat(resolved).containsEntry("status", "CLOSED")
                    .containsEntry("resolved_by_user_id", "user_publisher");
            assertThat(resolved.get("resolved_at")).isNotNull();
        } else {
            System.out.println("JAVA_PUBLICATION_REJECTED: " + publicationFailure.getMessage());
            // A failed attempt must not damage the approved label or published baseline.
            assertThat(labelStatus(jdbc)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("""
                    SELECT current_published_label_version_id FROM product WHERE product_id = ?
                    """, String.class, PRODUCT_ID)).isEqualTo(oldPointer);
            assertThat(jdbc.queryForMap(
                    "SELECT * FROM label_version WHERE label_version_id = ?", OLD_LABEL_ID))
                    .isEqualTo(oldPublishedLabel);
            assertThat(publicationCount(jdbc)).isZero();
            assertThat(publicationAuditCount(jdbc)).isZero();
        }
        // The current defect remains a failing regression; rejection is never an expected pass.
        softly.assertAll();
        var afterPublication = immutableHistorySnapshot(jdbc);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(immutableHistorySnapshot(jdbc)).isEqualTo(afterPublication);
    }

    private java.util.Map<String, Object> immutableHistorySnapshot(JdbcTemplate jdbc) {
        return java.util.Map.of(
                "labels", jdbc.queryForList("SELECT * FROM label_version ORDER BY label_version_id"),
                "products", jdbc.queryForList("SELECT * FROM product ORDER BY product_id"),
                "approvals", jdbc.queryForList("SELECT * FROM approval_record ORDER BY approval_record_id"),
                "publications", jdbc.queryForList("SELECT * FROM publication_record ORDER BY publication_record_id"),
                "audits", jdbc.queryForList("SELECT * FROM audit_event ORDER BY audit_event_id"));
    }

    @Test
    void preservesUnsupportedAndResolvedHistoryWhileRepairingAnEligibleTask() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        insertMinimumLegacyPrerequisites(jdbc);
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        var variants = java.util.List.of("repairable", "rejected", "open_draft", "published", "missing_approve",
                "mismatched_approve", "later_changes", "resolved_time", "resolved_actor", "different_draft",
                "reject_history", "untied_history", "reject_cache");
        int version = 100;
        for (String variant : variants) {
            String label = "label_v8_" + variant;
            String task = "task_v8_" + variant;
            String finding = "finding_v8_" + variant;
            String change = "change_v8_" + variant;
            String run = "run_v8_" + variant;
            jdbc.update("""
                    INSERT INTO label_version(label_version_id, product_id, formula_version_id, rule_set_version_id,
                        jurisdiction_code, version_number, raw_ingredient_text, lifecycle_status, is_current_published,
                        created_by_user_id, created_at, data_provenance_id)
                    SELECT ?, product_id, formula_version_id, rule_set_version_id, jurisdiction_code, ?,
                        raw_ingredient_text, 'APPROVED', 'N', created_by_user_id, created_at, data_provenance_id
                    FROM label_version WHERE label_version_id = ?
                    """, label, version++, LABEL_ID);
            jdbc.update("""
                    INSERT INTO change_request(change_request_id, change_request_code, change_type, status,
                        requested_at, requested_by_user_id, description, from_formula_version_id,
                        to_formula_version_id, data_provenance_id)
                    SELECT ?, ?, change_type, status, requested_at, requested_by_user_id, description,
                        from_formula_version_id, to_formula_version_id, data_provenance_id
                    FROM change_request WHERE change_request_id = 'change_pr67_v6_upgrade'
                    """, change, "code_v8_" + variant);
            jdbc.update("""
                    INSERT INTO impact_analysis_run(impact_analysis_run_id, run_code, change_request_id,
                        idempotency_key, rule_set_version_id, status, started_at, completed_at,
                        executed_by_user_id, data_provenance_id)
                    SELECT ?, ?, ?, ?, rule_set_version_id, status, started_at, completed_at,
                        executed_by_user_id, data_provenance_id
                    FROM impact_analysis_run WHERE impact_analysis_run_id = 'run_pr67_v6_upgrade'
                    """, run, "run_code_v8_" + variant, change, "impact-analysis:" + change);
            jdbc.update("""
                    INSERT INTO impact_finding(impact_finding_id, impact_analysis_run_id, product_id,
                        current_formula_version_id, proposed_formula_version_id, current_label_version_id,
                        classification, missing_allergen_codes, explanation, data_provenance_id)
                    SELECT ?, ?, product_id, current_formula_version_id,
                        proposed_formula_version_id, current_label_version_id, classification,
                        missing_allergen_codes, explanation, data_provenance_id
                    FROM impact_finding WHERE impact_finding_id = 'finding_pr67_v6_upgrade'
                    """, finding, run);
            jdbc.update("""
                    INSERT INTO review_task(review_task_id, impact_finding_id, product_id, current_label_version_id,
                        draft_label_version_id, target_label_version_id, status, decision, assigned_to_user_id,
                        created_by_user_id, created_at, data_provenance_id)
                    SELECT ?, ?, product_id, current_label_version_id, ?, ?, 'CLOSED', 'REQUEST_CHANGES',
                        assigned_to_user_id, created_by_user_id, created_at, data_provenance_id
                    FROM review_task WHERE review_task_id = ?
                    """, task, finding, label, label, TASK_ID);
            for (String decision : java.util.List.of("APPROVE", "REQUEST_CHANGES")) {
                jdbc.update("""
                        INSERT INTO approval_record(approval_record_id, label_version_id, review_task_id, decision,
                            decided_by_user_id, decided_at, comments, data_provenance_id)
                        VALUES (?, ?, ?, ?, 'user_approver', '2026-10-07 12:00:00',
                            'Controlled preservation boundary', 'prov_validation_fixture')
                        """, "approval_v8_" + variant + "_" + decision, label, task, decision);
            }
            switch (variant) {
                case "rejected" -> jdbc.update("UPDATE label_version SET lifecycle_status='REJECTED' WHERE label_version_id=?", label);
                case "open_draft" -> {
                    jdbc.update("UPDATE label_version SET lifecycle_status='DRAFT' WHERE label_version_id=?", label);
                    jdbc.update("UPDATE review_task SET status='OPEN' WHERE review_task_id=?", task);
                }
                case "published" -> {
                    jdbc.update("UPDATE label_version SET lifecycle_status='PUBLISHED' WHERE label_version_id=?", label);
                    jdbc.update("""
                            INSERT INTO publication_record(publication_record_id, label_version_id, published_by_user_id,
                                published_at, publication_channel, data_provenance_id)
                            VALUES ('publication_v8_preserved', ?, 'user_publisher', '2026-10-07 13:00:00',
                                'DEMO_RELEASE', 'prov_validation_fixture')
                            """, label);
                }
                case "missing_approve" -> jdbc.update("DELETE FROM approval_record WHERE review_task_id=? AND decision='APPROVE'", task);
                case "mismatched_approve" -> jdbc.update("UPDATE approval_record SET label_version_id=? WHERE review_task_id=? AND decision='APPROVE'", OLD_LABEL_ID, task);
                case "later_changes" -> jdbc.update("UPDATE approval_record SET decided_at='2026-10-07 12:00:01' WHERE review_task_id=? AND decision='REQUEST_CHANGES'", task);
                case "resolved_time" -> jdbc.update("UPDATE review_task SET resolved_at='2026-10-07 13:00:00' WHERE review_task_id=?", task);
                case "resolved_actor" -> jdbc.update("UPDATE review_task SET resolved_by_user_id='user_approver' WHERE review_task_id=?", task);
                case "different_draft" -> jdbc.update("UPDATE review_task SET draft_label_version_id=? WHERE review_task_id=?", OLD_LABEL_ID, task);
                case "reject_history" -> jdbc.update("""
                        INSERT INTO approval_record(approval_record_id, label_version_id, review_task_id, decision,
                            decided_by_user_id, decided_at, comments, data_provenance_id)
                        VALUES ('approval_v8_reject_history_REJECT', ?, ?, 'REJECT', 'user_approver',
                            '2026-10-07 12:00:00', 'Ambiguous invalid history preserved', 'prov_validation_fixture')
                        """, label, task);
                case "untied_history" -> jdbc.update("UPDATE approval_record SET decided_at='2026-10-07 11:59:59' WHERE review_task_id=? AND decision='APPROVE'", task);
                case "reject_cache" -> jdbc.update("UPDATE review_task SET decision='REJECT' WHERE review_task_id=?", task);
                default -> { }
            }
        }
        var immutableBefore = immutableHistorySnapshot(jdbc);
        var untouchedTasks = jdbc.queryForList("SELECT * FROM review_task WHERE review_task_id <> 'task_v8_repairable' ORDER BY review_task_id");
        Integer checksumBefore = jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='7'", Integer.class);
        var upgraded = Flyway.configure().dataSource(dataSource).load();
        upgraded.migrate();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(immutableHistorySnapshot(jdbc)).isEqualTo(immutableBefore);
        assertThat(jdbc.queryForList("SELECT * FROM review_task WHERE review_task_id <> 'task_v8_repairable' ORDER BY review_task_id"))
                .isEqualTo(untouchedTasks);
        assertThat(jdbc.queryForMap("SELECT status, decision, resolved_at, resolved_by_user_id FROM review_task WHERE review_task_id='task_v8_repairable'"))
                .containsEntry("status", "IN_REVIEW").containsEntry("decision", "APPROVE")
                .containsEntry("resolved_at", null).containsEntry("resolved_by_user_id", null);
        assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='7'", Integer.class)).isEqualTo(checksumBefore);
        System.out.println("V8_PRESERVATION_MATRIX: repaired=1 preserved=12 history/labels/products/publications/audits=unchanged");
    }

    private void insertMinimumLegacyPrerequisites(JdbcTemplate jdbc) {
        assertThat(jdbc.update("""
                INSERT INTO label_version(
                    label_version_id, product_id, formula_version_id, rule_set_version_id,
                    jurisdiction_code, version_number, raw_ingredient_text, lifecycle_status,
                    is_current_published, created_by_user_id, created_at, data_provenance_id)
                SELECT ?, product_id, formula_version_id, rule_set_version_id, jurisdiction_code,
                       version_number + 1, raw_ingredient_text, 'DRAFT', 'N', 'user_label_officer',
                       UTC_TIMESTAMP(), data_provenance_id
                FROM label_version WHERE label_version_id = ?
                """, LABEL_ID, OLD_LABEL_ID)).isEqualTo(1);
        // Minimal validation/task inputs isolate migration compatibility, not validator/M1 acceptance.
        jdbc.update("""
                INSERT INTO validation_run(
                    validation_run_id, label_version_id, rule_set_version_id, status,
                    ran_by_user_id, ran_at, summary, data_provenance_id)
                VALUES ('validation_pr67_v6_upgrade', ?, 'ruleset_us_falcpa_demo_v1', 'PASSED',
                        'user_label_officer', UTC_TIMESTAMP(), 'Migration prerequisite fixture', 'prov_validation_fixture')
                """, LABEL_ID);
        jdbc.update("""
                INSERT INTO change_request(
                    change_request_id, change_request_code, change_type, status, requested_at,
                    requested_by_user_id, description, from_formula_version_id, to_formula_version_id, data_provenance_id)
                VALUES ('change_pr67_v6_upgrade', 'code_pr67_v6_upgrade', 'FORMULA', 'ANALYZED', UTC_TIMESTAMP(),
                        'user_label_officer', 'Migration prerequisite fixture',
                        'formula_1106285_v1', 'formula_1106285_v1', 'prov_project_seed')
                """);
        jdbc.update("""
                INSERT INTO impact_analysis_run(
                    impact_analysis_run_id, run_code, change_request_id, idempotency_key,
                    rule_set_version_id, status, started_at, completed_at, executed_by_user_id, data_provenance_id)
                VALUES ('run_pr67_v6_upgrade', 'run-code_pr67_v6_upgrade', 'change_pr67_v6_upgrade',
                        'impact-analysis:change_pr67_v6_upgrade', 'ruleset_us_falcpa_demo_v1', 'COMPLETED',
                        UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'user_label_officer', 'prov_project_seed')
                """);
        assertThat(jdbc.update("""
                INSERT INTO impact_finding(
                    impact_finding_id, impact_analysis_run_id, product_id, current_formula_version_id,
                    proposed_formula_version_id, current_label_version_id, classification,
                    missing_allergen_codes, explanation, data_provenance_id)
                SELECT 'finding_pr67_v6_upgrade', 'run_pr67_v6_upgrade', lv.product_id, lv.formula_version_id,
                       lv.formula_version_id, p.current_published_label_version_id, 'REVIEW_REQUIRED', JSON_ARRAY(),
                       'Migration prerequisite fixture', 'prov_project_seed'
                FROM label_version lv JOIN product p ON p.product_id = lv.product_id
                WHERE lv.label_version_id = ?
                """, LABEL_ID)).isEqualTo(1);
        assertThat(jdbc.update("""
                INSERT INTO review_task(
                    review_task_id, impact_finding_id, product_id, current_label_version_id,
                    draft_label_version_id, status, assigned_to_user_id, created_by_user_id, created_at, data_provenance_id)
                SELECT ?, impact_finding_id, product_id, current_label_version_id, ?, 'OPEN',
                       'user_approver', 'user_label_officer', UTC_TIMESTAMP(), data_provenance_id
                FROM impact_finding WHERE impact_finding_id = 'finding_pr67_v6_upgrade'
                """, TASK_ID, LABEL_ID)).isEqualTo(1);
    }

    private String labelStatus(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT lifecycle_status FROM label_version WHERE label_version_id = ?",
                String.class, LABEL_ID);
    }

    private int publicationCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM publication_record WHERE label_version_id = ?",
                Integer.class, LABEL_ID);
    }

    private int publicationAuditCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event WHERE event_type = 'LABEL_PUBLISHED' AND entity_id = ?
                """, Integer.class, LABEL_ID);
    }
}
