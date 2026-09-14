package com.spectrace.validation;

import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RuleSetVersionRepositoryIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String ACTIVE_RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String DRAFT_RULE_SET = "ruleset_us_falcpa_demo_v2";

    @Autowired
    private RuleSetVersionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreDraftFixture() {
        jdbcTemplate.update(
                "UPDATE rule_set_version SET lifecycle_status = 'DRAFT' WHERE rule_set_version_id = ?",
                DRAFT_RULE_SET
        );
        jdbcTemplate.update(
                "DELETE FROM rule_definition WHERE rule_definition_id LIKE ?",
                "scrum28-inactive-%"
        );
    }

    @Test
    void readsActiveV1WithItsRuleDefinitions() {
        var ruleSet = repository.findById(ACTIVE_RULE_SET).orElseThrow();

        assertThat(ruleSet.ruleSetVersionId()).isEqualTo(ACTIVE_RULE_SET);
        assertThat(ruleSet.lifecycleStatus()).isEqualTo(RuleSetLifecycleStatus.ACTIVE);
        assertThat(ruleSet.effectiveFrom()).hasToString("2026-01-01");
        assertThat(ruleSet.effectiveTo()).isNull();
        assertThat(ruleSet.demoOnly()).isTrue();
        assertThat(ruleSet.ruleDefinitions()).hasSize(4);
        assertThat(ruleSet.ruleDefinitions())
                .allSatisfy(rule -> assertThat(rule.ruleSetVersionId()).isEqualTo(ACTIVE_RULE_SET));
        assertThat(ruleSet.ruleDefinitions())
                .allSatisfy(rule -> assertThat(rule.active()).isTrue());
    }

    @Test
    void readsDraftV2AndActiveOnlyLookupDoesNotAcceptIt() {
        var ruleSet = repository.findById(DRAFT_RULE_SET).orElseThrow();

        assertThat(ruleSet.lifecycleStatus()).isEqualTo(RuleSetLifecycleStatus.DRAFT);
        assertThat(ruleSet.ruleDefinitions()).hasSize(4);
        assertThat(repository.findActiveById(DRAFT_RULE_SET)).isEmpty();
        assertThat(repository.findActiveById(ACTIVE_RULE_SET)).isPresent();
    }

    @Test
    void retiredRuleSetIsReadableButNotReturnedByActiveOnlyLookup() {
        jdbcTemplate.update(
                "UPDATE rule_set_version SET lifecycle_status = 'RETIRED' WHERE rule_set_version_id = ?",
                DRAFT_RULE_SET
        );

        assertThat(repository.findById(DRAFT_RULE_SET).orElseThrow().lifecycleStatus())
                .isEqualTo(RuleSetLifecycleStatus.RETIRED);
        assertThat(repository.findActiveById(DRAFT_RULE_SET)).isEmpty();
    }

    @Test
    void mapsAnInactiveRuleDefinitionToFalseWithoutSilentFallback() {
        String ruleDefinitionId = "scrum28-inactive-flag";
        jdbcTemplate.update(
                """
                INSERT INTO rule_definition(
                  rule_definition_id, rule_set_version_id, rule_code, rule_type,
                  target_allergen_id, pattern_text, severity, is_active, description
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ruleDefinitionId,
                ACTIVE_RULE_SET,
                "SCRUM28_INACTIVE_FLAG",
                "LABEL_DECLARATION_VALIDATION",
                null,
                "test inactive rule",
                "WARNING",
                "N",
                "SCRUM-28 Y/N mapping fixture"
        );

        assertThat(repository.findById(ACTIVE_RULE_SET).orElseThrow().ruleDefinitions())
                .filteredOn(rule -> rule.ruleDefinitionId().equals(ruleDefinitionId))
                .singleElement()
                .extracting(rule -> rule.active())
                .isEqualTo(false);
    }

    @Test
    void unknownRuleSetReturnsEmptyForBothLookupModes() {
        assertThat(repository.findById("does-not-exist")).isEmpty();
        assertThat(repository.findActiveById("does-not-exist")).isEmpty();
    }
}
