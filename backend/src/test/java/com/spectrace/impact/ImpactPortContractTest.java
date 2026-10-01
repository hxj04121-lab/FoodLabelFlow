package com.spectrace.impact;

import com.spectrace.catalog.application.port.RelevantProductLookupPort.RelevantProduct;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.Lifecycle;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.SpecificationVersionFacts;
import com.spectrace.impact.application.port.ImpactIntegration.Permission;
import com.spectrace.impact.application.port.ReviewTaskPort.OpenReviewTask;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.support.InMemoryImpactPorts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactPortContractTest {
    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");

    @Test
    void permissionsAreTheCanonicalSeededCodes() throws IOException {
        String seed = ImpactDomainContractTest.migration("V3__baseline_seed.sql");

        for (Permission permission : Permission.values()) {
            assertThat(seed).contains("'" + permission.code() + "'");
        }
        assertThat(Permission.RUN_IMPACT.code()).isEqualTo("IMPACT.RUN");
        assertThat(Permission.CREATE_CHANGE_REQUEST.code()).isEqualTo("CHANGE_REQUEST.CREATE");
    }

    @Test
    void changeRequestsRoundTripAndKeepUniqueIdsAndCodes() {
        var repository = new InMemoryImpactPorts.ChangeRequests();
        var request = ImpactDomainContractTest.changeRequest(new VersionChange("spec-1", "spec-2"));
        repository.save(request);

        assertThat(repository.findById("cr-1")).contains(request);
        assertThat(repository.findById("missing")).isEmpty();
        assertThat(repository.findOpenByVersionChange(ChangeType.INGREDIENT_SPEC, new VersionChange("spec-1", "spec-2")))
                .contains(request);
        assertThat(repository.findOpenByVersionChange(ChangeType.INGREDIENT_SPEC, new VersionChange("spec-2", "spec-1")))
                .isEmpty();
        assertThat(repository.findOpenByVersionChange(ChangeType.FORMULA, new VersionChange("spec-1", "spec-2")))
                .isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> repository.save(request));
    }

    @Test
    void runsAreListedOldestFirstPerChangeRequest() {
        var runs = new InMemoryImpactPorts.Runs();
        var later = run("run-b", "RUN-B", "cr-1", NOW.plusSeconds(60));
        var earlier = run("run-a", "RUN-A", "cr-1", NOW);
        runs.save(later);
        runs.save(earlier);
        runs.save(run("run-c", "RUN-C", "cr-2", NOW));

        assertThat(runs.findByChangeRequestId("cr-1")).containsExactly(earlier, later);
        assertThat(runs.findByChangeRequestId("cr-unknown")).isEmpty();
        assertThat(runs.findById("run-c")).isPresent();
        assertThatIllegalStateException().isThrownBy(() ->
                runs.save(run("run-d", "RUN-A", "cr-1", NOW)));
    }

    @Test
    void findingsAreOrderedByProductAndUniquePerRunAndProduct() {
        var findings = new InMemoryImpactPorts.Findings();
        var productB = finding("finding-b", "run-1", "product-b", ImpactClassification.NO_ACTION);
        var productA = finding("finding-a", "run-1", "product-a", ImpactClassification.REVIEW_REQUIRED);
        findings.saveAll("run-1", List.of(productB, productA));

        assertThat(findings.findByRunId("run-1")).containsExactly(productA, productB);
        assertThat(findings.findByRunId("run-2")).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> findings.saveAll("run-1", List.of(
                finding("finding-c", "run-1", "product-a", ImpactClassification.NO_ACTION))));
        assertThatIllegalArgumentException().isThrownBy(() -> findings.saveAll("run-2", List.of(
                finding("finding-d", "run-1", "product-d", ImpactClassification.NO_ACTION))));
    }

    @Test
    void onlyReviewRequiredFindingsOpenExactlyOneReviewTask() {
        var tasks = new InMemoryImpactPorts.ReviewTasks();
        var reviewRequired = finding("finding-1", "run-1", "product-1", ImpactClassification.REVIEW_REQUIRED);
        var noAction = finding("finding-2", "run-1", "product-2", ImpactClassification.NO_ACTION);

        String taskId = tasks.open(new OpenReviewTask(reviewRequired, "reviewer-1", "user-1", NOW));

        assertThat(taskId).isNotBlank();
        assertThat(tasks.opened()).singleElement()
                .satisfies(task -> assertThat(task.finding()).isEqualTo(reviewRequired));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OpenReviewTask(noAction, "reviewer-1", "user-1", NOW));
        assertThatIllegalStateException().isThrownBy(() ->
                tasks.open(new OpenReviewTask(reviewRequired, "reviewer-2", "user-1", NOW)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OpenReviewTask(reviewRequired, " ", "user-1", NOW));
        assertThatNullPointerException().isThrownBy(() ->
                new OpenReviewTask(reviewRequired, "reviewer-1", "user-1", null));
    }

    @Test
    void relevantProductsNeedFormulaEvidenceAndMayLackAPublishedLabel() {
        var itemIds = new ArrayList<>(List.of("fi_1_v2_1"));
        var withLabel = new RelevantProduct("product-1", "formula_1_v2", "label_1_v1", itemIds);
        itemIds.clear();
        var withoutLabel = new RelevantProduct("product-0", "formula_0_v2", null, List.of("fi_0_v2_1"));
        var lookup = new InMemoryImpactPorts.RelevantProducts()
                .add("mat_chocolate_base", withLabel)
                .add("mat_chocolate_base", withoutLabel);

        assertThat(lookup.findProductsUsingMaterial("mat_chocolate_base")).containsExactly(withoutLabel, withLabel);
        assertThat(lookup.findProductsUsingMaterial("mat_unused")).isEmpty();
        assertThat(withLabel.matchingFormulaItemIds()).containsExactly("fi_1_v2_1");
        assertThat(withLabel.hasPublishedLabel()).isTrue();
        assertThat(withoutLabel.hasPublishedLabel()).isFalse();
        assertThatThrownBy(() -> withLabel.matchingFormulaItemIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RelevantProduct("product-1", "formula_1_v2", "label_1_v1", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RelevantProduct("product-1", "formula_1_v2", "label_1_v1", List.of("fi-1", "fi-1")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RelevantProduct("product-1", "formula_1_v2", " ", List.of("fi-1")));
    }

    @Test
    void specificationVersionsAreExactLookupsWithEffectiveDates() {
        LocalDate effective = LocalDate.of(2026, 9, 1);
        var released = new SpecificationVersionFacts(
                "spec_chocolate_v2", "mat_chocolate_base", 2, Lifecycle.RELEASED, effective);
        var lookup = new InMemoryImpactPorts.SpecificationVersions().add(released);

        assertThat(lookup.findById("spec_chocolate_v2")).contains(released);
        assertThat(lookup.lockById("spec_chocolate_v2")).contains(released);
        assertThat(lookup.locked()).containsExactly("spec_chocolate_v2");
        assertThat(lookup.findById("spec_chocolate_v3")).isEmpty();
        assertThat(lookup.supplierMaterialExists("mat_chocolate_base")).isTrue();
        assertThat(lookup.supplierMaterialExists("mat_unknown")).isFalse();
        assertThat(released.isEffectiveOn(effective)).isTrue();
        assertThat(released.isEffectiveOn(effective.minusDays(1))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SpecificationVersionFacts("spec-1", "mat-1", 0, Lifecycle.RELEASED, effective));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SpecificationVersionFacts("spec-1", " ", 1, Lifecycle.DRAFT, effective));
        assertThatNullPointerException().isThrownBy(() ->
                new SpecificationVersionFacts("spec-1", "mat-1", 1, null, effective));
        assertThatNullPointerException().isThrownBy(() ->
                new SpecificationVersionFacts("spec-1", "mat-1", 1, Lifecycle.RELEASED, null));
    }

    private static ImpactAnalysisRun run(String runId, String runCode, String changeRequestId, Instant startedAt) {
        return new ImpactAnalysisRun(runId, runCode, changeRequestId, "ruleset-1",
                ImpactRunStatus.COMPLETED, startedAt, startedAt, "user-1", "prov-1");
    }

    private static ImpactFinding finding(
            String findingId, String runId, String productId, ImpactClassification classification) {
        List<String> missing = classification.requiresReviewTask() ? List.of("SOY") : List.of();
        return new ImpactFinding(findingId, runId, productId, "formula-v1", "formula-v2",
                "label-v1", classification, missing, "explanation", "prov-1");
    }
}
