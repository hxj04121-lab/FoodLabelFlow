package com.spectrace.validation;

import com.spectrace.support.MySqlIntegrationTestSupport;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ValidationRunRepositoryIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String RUN_PREFIX = "scrum28-run-";

    @Autowired
    private ValidationRunRepository runRepository;

    @Autowired
    private ValidationResultRepository resultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeRoundTripRows() {
        jdbcTemplate.update(
                "DELETE FROM validation_result WHERE validation_run_id LIKE ?",
                RUN_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM validation_run WHERE validation_run_id LIKE ?",
                RUN_PREFIX + "%"
        );
    }

    @Test
    void roundTripsRunAndAllResultsWithStableOrderingAndExplicitMappings() {
        String runId = RUN_PREFIX + "round-trip";
        Instant ranAt = Instant.parse("2026-09-13T10:11:12Z");
        ValidationRun run = new ValidationRun(
                runId,
                "label_1106963_v1",
                "ruleset_us_falcpa_demo_v1",
                ValidationStatus.FAILED,
                "user_label_officer",
                ranAt,
                "one blocking finding",
                "prov_project_seed"
        );
        List<ValidationResult> results = List.of(
                new ValidationResult(
                        runId + "-01",
                        runId,
                        "rule_v1_milk_ingredient",
                        "MILK_DECLARATION_MISSING",
                        ValidationSeverity.ERROR,
                        false,
                        true,
                        "MILK declaration is missing"
                ),
                new ValidationResult(
                        runId + "-02",
                        runId,
                        null,
                        "LABEL_DECLARATION_PRESENT",
                        ValidationSeverity.INFO,
                        true,
                        false,
                        "Label declaration is present"
                )
        );

        runRepository.save(run);
        resultRepository.saveAll(runId, results);

        assertThat(runRepository.findById(runId)).contains(run);
        assertThat(resultRepository.findByRunId(runId)).containsExactlyElementsOf(results);
        assertThat(resultRepository.findByRunId(runId))
                .extracting(ValidationResult::validationResultId)
                .containsExactly(runId + "-01", runId + "-02");
        assertThat(resultRepository.findByRunId(runId).getFirst())
                .satisfies(result -> {
                    assertThat(result.passed()).isFalse();
                    assertThat(result.blocking()).isTrue();
                    assertThat(result.severity()).isEqualTo(ValidationSeverity.ERROR);
                    assertThat(result.ruleDefinitionId()).isEqualTo("rule_v1_milk_ingredient");
                });
    }

    @Test
    void supportsPassedRunAndYFlagsAfterReadingFromMySql() {
        String runId = RUN_PREFIX + "passed";
        ValidationRun run = new ValidationRun(
                runId,
                "label_1108162_v1",
                "ruleset_us_falcpa_demo_v1",
                ValidationStatus.PASSED,
                "user_label_officer",
                Instant.parse("2026-09-13T11:12:13Z"),
                "all checks passed",
                "prov_project_seed"
        );
        ValidationResult result = new ValidationResult(
                runId + "-01",
                runId,
                "rule_v1_label_contains",
                "LABEL_DECLARATION_PRESENT",
                ValidationSeverity.INFO,
                true,
                false,
                "Label declaration is valid"
        );

        runRepository.save(run);
        resultRepository.saveAll(runId, List.of(result));

        assertThat(runRepository.findById(runId).orElseThrow().status()).isEqualTo(ValidationStatus.PASSED);
        assertThat(runRepository.findById(runId).orElseThrow().ranAt())
                .isEqualTo(Instant.parse("2026-09-13T11:12:13Z"));
        assertThat(resultRepository.findByRunId(runId).getFirst())
                .satisfies(read -> {
                    assertThat(read.passed()).isTrue();
                    assertThat(read.blocking()).isFalse();
                });
    }

    @Test
    void unknownRunReturnsEmptyRunAndEmptyResults() {
        assertThat(runRepository.findById("does-not-exist")).isEmpty();
        assertThat(resultRepository.findByRunId("does-not-exist")).isEmpty();
    }
}
