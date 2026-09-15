package com.spectrace.validation;

import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPersistencePortContractTest {

    @Test
    void repositoriesAreUsableWithInMemoryAdaptersAndKeepRunTraceability() {
        var ruleSet = new RuleSetVersion("rules-1", "TEST", "1", "US",
                RuleSetLifecycleStatus.ACTIVE, LocalDate.of(2026, 9, 1), null,
                true, "Test rules", "prov-test", List.of());
        var run = new ValidationRun("run-1", "label-1", ruleSet.ruleSetVersionId(),
                ValidationStatus.PASSED, "user-1", Instant.parse("2026-09-15T00:00:00Z"),
                "all checks passed", "prov-test");
        var result = new ValidationResult("result-1", run.validationRunId(), null,
                "CHECK", ValidationSeverity.INFO, true, false, "passed");

        RuleSetVersionRepository ruleSets = new InMemoryRuleSets(ruleSet);
        ValidationRunRepository runs = new InMemoryRuns();
        ValidationResultRepository results = new InMemoryResults();
        runs.save(run);
        results.saveAll(run.validationRunId(), List.of(result));

        assertThat(ruleSets.findActiveById(run.ruleSetVersionId())).contains(ruleSet);
        assertThat(runs.findById(run.validationRunId())).contains(run);
        assertThat(results.findByRunId(run.validationRunId())).containsExactly(result);
        assertThat(result.validationRunId()).isEqualTo(run.validationRunId());
    }

    private record InMemoryRuleSets(RuleSetVersion value) implements RuleSetVersionRepository {
        @Override public Optional<RuleSetVersion> findById(String id) {
            return value.ruleSetVersionId().equals(id) ? Optional.of(value) : Optional.empty();
        }

        @Override public Optional<RuleSetVersion> findActiveById(String id) {
            return value.ruleSetVersionId().equals(id) && value.lifecycleStatus() == RuleSetLifecycleStatus.ACTIVE
                    ? Optional.of(value) : Optional.empty();
        }
    }

    private static final class InMemoryRuns implements ValidationRunRepository {
        private final HashMap<String, ValidationRun> values = new HashMap<>();

        @Override public void save(ValidationRun value) { values.put(value.validationRunId(), value); }

        @Override public Optional<ValidationRun> findById(String id) { return Optional.ofNullable(values.get(id)); }
    }

    private static final class InMemoryResults implements ValidationResultRepository {
        private final HashMap<String, List<ValidationResult>> values = new HashMap<>();

        @Override public void saveAll(String runId, List<ValidationResult> results) {
            values.put(runId, List.copyOf(results));
        }

        @Override public List<ValidationResult> findByRunId(String runId) {
            return values.getOrDefault(runId, List.of());
        }
    }
}
