package com.spectrace.impact.application;

import com.spectrace.audit.application.port.ImpactAuditEventPort;
import com.spectrace.catalog.application.port.RelevantProductLookupPort.RelevantProduct;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.Lifecycle;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.SpecificationVersionFacts;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.impact.application.ImpactAnalysisView.FindingView;
import com.spectrace.impact.application.port.ImpactIntegration;
import com.spectrace.impact.application.strategy.ImpactStrategy;
import com.spectrace.impact.application.strategy.ImpactStrategyRegistry;
import com.spectrace.impact.application.strategy.ProductImpactAssessment;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import com.spectrace.impact.support.InMemoryImpactPorts;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeImpactAnalysisServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T08:15:30.700Z");
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String MATERIAL = "mat_chocolate_base";
    private static final String CR = "cr-1";

    private final InMemoryImpactPorts.ChangeRequests changeRequests = new InMemoryImpactPorts.ChangeRequests();
    private final InMemoryImpactPorts.SpecificationVersions specifications = new InMemoryImpactPorts.SpecificationVersions()
            .add(spec("spec_chocolate_v1", 1, Lifecycle.RETIRED))
            .add(spec("spec_chocolate_v2", 2, Lifecycle.RELEASED));
    private final InMemoryImpactPorts.RelevantProducts products = new InMemoryImpactPorts.RelevantProducts();
    private final InMemoryImpactPorts.Runs runs = new InMemoryImpactPorts.Runs();
    private final InMemoryImpactPorts.Findings findings = new InMemoryImpactPorts.Findings();
    private final InMemoryImpactPorts.ReviewTaskLinkages tasks = new InMemoryImpactPorts.ReviewTaskLinkages();
    private final List<String> audits = new ArrayList<>();
    private final ImpactAuditEventPort audit = (actor, runId, changeRequestId, outcome, provenance) ->
            audits.add(actor + "|" + runId + "|" + changeRequestId + "|" + outcome + "|" + provenance);
    private final Map<String, List<String>> missingByProduct = new HashMap<>();
    private final Map<String, RuntimeException> failureByProduct = new HashMap<>();
    private final FakeIntegration integration = new FakeIntegration();
    private final AtomicInteger sequence = new AtomicInteger();
    private Optional<RuleSetVersion> activeRuleSet = Optional.of(ruleSet());

    private final ImpactStrategy strategy = new ImpactStrategy() {
        @Override
        public ChangeType changeType() {
            return ChangeType.INGREDIENT_SPEC;
        }

        @Override
        public ProductImpactAssessment assess(ChangeRequest change, RelevantProductTarget product, String ruleSetVersionId) {
            RuntimeException failure = failureByProduct.get(product.productId());
            if (failure != null) {
                throw failure;
            }
            return new ProductImpactAssessment(product.productId(), product.productId() + "_formula_n",
                    product.currentFormulaVersionId(), product.currentLabelVersionId(),
                    missingByProduct.getOrDefault(product.productId(), List.of()), "explained " + ruleSetVersionId);
        }
    };

    private final ChangeImpactAnalysisService service = new ChangeImpactAnalysisService(
            changeRequests, specifications, id -> activeRuleSet.filter(found -> found.ruleSetVersionId().equals(id)),
            new RelevantProductDiscovery(products),
            new ImpactStrategyRegistry(List.of(strategy), EnumSet.of(ChangeType.INGREDIENT_SPEC)),
            new ImpactAnalysisApplicationService(runs, findings, tasks, audit),
            runs, findings, tasks, integration, Clock.fixed(NOW, ZoneOffset.UTC),
            () -> "id-" + sequence.incrementAndGet());

    @Test
    void aFirstRunPersistsFindingsOpensTasksForReviewRequiredOnlyAndMarksTheRequestAnalyzed() {
        submitted();
        relevant("prod_c", "prod_a", "prod_b");
        missingByProduct.put("prod_b", List.of("SOY"));

        ImpactAnalysisView view = service.run(CR, RULE_SET);

        assertThat(view.created()).isTrue();
        ImpactAnalysisRun run = view.run();
        assertThat(run).isEqualTo(runs.findById(run.impactAnalysisRunId()).orElseThrow());
        assertThat(run.status()).isEqualTo(ImpactRunStatus.COMPLETED);
        assertThat(run.startedAt()).isEqualTo(Instant.parse("2026-10-06T08:15:30Z")).isEqualTo(run.completedAt());
        assertThat(run.executedByUserId()).isEqualTo("user_change_manager");
        assertThat(run.ruleSetVersionId()).isEqualTo(RULE_SET);
        assertThat(run.dataProvenanceId()).isEqualTo("prov_scenario_input");
        assertThat(run.runCode()).isEqualTo("IAR-" + run.impactAnalysisRunId());

        assertThat(view.findings()).extracting(found -> found.finding().productId())
                .containsExactly("prod_a", "prod_b", "prod_c");
        assertThat(view.count(ImpactClassification.NO_ACTION)).isEqualTo(2);
        assertThat(view.count(ImpactClassification.REVIEW_REQUIRED)).isEqualTo(1);
        assertThat(findings.findByRunId(run.impactAnalysisRunId())).hasSize(3)
                .allSatisfy(finding -> assertThat(finding.dataProvenanceId()).isEqualTo("prov_scenario_input"));

        FindingView review = view.findings().get(1);
        ReviewTaskLinkage task = review.reviewTask();
        assertThat(tasks.saved()).containsExactly(task);
        assertThat(task.impactFindingId()).isEqualTo(review.finding().impactFindingId());
        assertThat(task.productId()).isEqualTo("prod_b");
        assertThat(task.currentLabelVersionId()).isEqualTo("label_prod_b_v1");
        assertThat(task.draftLabelVersionId()).isNull();
        assertThat(task.status()).isEqualTo(ReviewTaskStatus.OPEN);
        assertThat(task.assignedToUserId()).isEqualTo("user_label_officer");
        assertThat(task.createdByUserId()).isEqualTo("user_change_manager");
        assertThat(view.findings().get(0).reviewTask()).isNull();

        assertThat(changeRequests.findById(CR).orElseThrow().status()).isEqualTo(ChangeRequestStatus.ANALYZED);
        assertThat(changeRequests.locked()).containsExactly(CR);
        assertThat(runs.replayReads()).as("SUBMITTED first runs do not lock absent run keys").isEmpty();
        assertThat(audits).containsExactly(
                "user_change_manager|" + run.impactAnalysisRunId() + "|" + CR + "|COMPLETED|prov_scenario_input");
        assertThat(integration.calls).containsExactly("requireActor:RUN_IMPACT", "reviewTaskAssignee");
    }

    @Test
    void replayingWithTheSameRuleSetReturnsTheExistingAnalysisWithoutWriting() {
        submitted();
        relevant("prod_a", "prod_b");
        missingByProduct.put("prod_a", List.of("SOY"));
        ImpactAnalysisView first = service.run(CR, RULE_SET);
        missingByProduct.clear();
        relevant("prod_z");

        ImpactAnalysisView replay = service.run(CR, RULE_SET);

        assertThat(replay.created()).isFalse();
        assertThat(replay.run()).isEqualTo(first.run());
        assertThat(replay.findings()).isEqualTo(first.findings());
        assertThat(runs.replayReads()).as("ANALYZED replay reads the committed run").containsExactly(CR);
        assertThat(tasks.saved()).hasSize(1);
        assertThat(audits).hasSize(1);
        assertThat(service.get(first.run().impactAnalysisRunId()).findings()).isEqualTo(first.findings());
    }

    @Test
    void replayingWithAnotherRuleSetIsAConflict() {
        submitted();
        service.run(CR, RULE_SET);

        assertFailure(() -> service.run(CR, "ruleset_us_falcpa_demo_v2"), 409, "DATA_CONFLICT");
        assertThat(audits).hasSize(1);
    }

    @Test
    void anUnknownOrNonIngredientSpecRequestIsNotFound() {
        assertFailure(() -> service.run("cr-missing", RULE_SET), 404, "RESOURCE_NOT_FOUND");
        changeRequests.save(new ChangeRequest("cr-formula", "CR-formula", ChangeType.FORMULA,
                ChangeRequestStatus.SUBMITTED, NOW, "user_change_manager", "Formula change",
                new VersionChange("formula_a_v1", "formula_a_v2"), "prov_scenario_input"));

        assertFailure(() -> service.run("cr-formula", RULE_SET), 404, "RESOURCE_NOT_FOUND");
        assertNothingWritten();
    }

    @Test
    void aCancelledRequestCannotBeAnalysed() {
        changeRequests.save(request(ChangeRequestStatus.CANCELLED));

        assertFailure(() -> service.run(CR, RULE_SET), 409, "DATA_CONFLICT");
        assertNothingWritten();
    }

    @Test
    void anInactiveOrUnknownRuleSetIsAPreconditionFailure() {
        submitted();
        activeRuleSet = Optional.empty();

        assertFailure(() -> service.run(CR, RULE_SET), 422, "RULE_SET_NOT_ACTIVE");
        assertNothingWritten();
    }

    @Test
    void aBlankRuleSetIsInvalidAfterThePermissionCheck() {
        submitted();

        assertFailure(() -> service.run(CR, " "), 400, "INVALID_REQUEST");
        assertThat(integration.calls).containsExactly("requireActor:RUN_IMPACT");
        assertThat(changeRequests.locked()).isEmpty();
    }

    @Test
    void permissionIsCheckedBeforeAnyRead() {
        submitted();
        integration.deny = true;

        assertThatThrownBy(() -> service.run(CR, RULE_SET)).isInstanceOf(AuthorizationDeniedException.class);
        assertThat(changeRequests.locked()).isEmpty();
        assertNothingWritten();
    }

    @Test
    void aPreconditionOnAnyProductFailsTheWholeRunBeforeTheFirstWrite() {
        submitted();
        relevant("prod_a", "prod_b", "prod_c");
        missingByProduct.put("prod_a", List.of("SOY"));
        failureByProduct.put("prod_c",
                ImpactFailure.precondition("FORMULA_ADOPTION_PENDING", "prod_c has not adopted the target"));

        assertFailure(() -> service.run(CR, RULE_SET), 422, "FORMULA_ADOPTION_PENDING");
        assertNothingWritten();
        assertThat(integration.calls).doesNotContain("reviewTaskAssignee");
    }

    @Test
    void aRelevantProductWithoutAPublishedLabelFailsTheRun() {
        submitted();
        products.add(MATERIAL, new RelevantProduct("prod_a", "formula_prod_a_v2", null, List.of("fi_prod_a_1")));

        assertFailure(() -> service.run(CR, RULE_SET), 422, "PUBLISHED_LABEL_MISSING");
        assertNothingWritten();
    }

    @Test
    void aRunWithOnlyNoActionFindingsNeedsNoAssigneeAndARunWithNoProductsIsStillCompleted() {
        submitted();
        relevant("prod_a");

        assertThat(service.run(CR, RULE_SET).findings()).singleElement()
                .satisfies(found -> assertThat(found.reviewTask()).isNull());
        assertThat(integration.calls).doesNotContain("reviewTaskAssignee");

        changeRequests.save(new ChangeRequest("cr-2", "CR-2", ChangeType.INGREDIENT_SPEC,
                ChangeRequestStatus.SUBMITTED, NOW, "user_change_manager", "Unused material",
                new VersionChange("spec_unused_v1", "spec_unused_v2"), "prov_scenario_input"));
        specifications.add(new SpecificationVersionFacts("spec_unused_v2", "mat_unused", 2, Lifecycle.RELEASED,
                LocalDate.of(2026, 9, 1)));
        ImpactAnalysisView empty = service.run("cr-2", RULE_SET);
        assertThat(empty.created()).isTrue();
        assertThat(empty.findings()).isEmpty();
        assertThat(changeRequests.findById("cr-2").orElseThrow().status()).isEqualTo(ChangeRequestStatus.ANALYZED);
    }

    @Test
    void aMissingAssigneeIsAServerFaultBeforeAnyWrite() {
        submitted();
        relevant("prod_a");
        missingByProduct.put("prod_a", List.of("SOY"));
        integration.assignee = null;

        assertThatIllegalStateException().isThrownBy(() -> service.run(CR, RULE_SET));
        assertNothingWritten();
    }

    @Test
    void queryingAnUnknownAnalysisIsNotFoundAndQueryingNeedsOnlyAnIdentity() {
        assertFailure(() -> service.get("missing"), 404, "RESOURCE_NOT_FOUND");
        assertThat(integration.calls).containsExactly("authenticate");
    }

    @Test
    void aReviewRequiredFindingWithoutItsTaskIsReportedNotHidden() {
        submitted();
        relevant("prod_a");
        missingByProduct.put("prod_a", List.of("SOY"));
        ImpactAnalysisView first = service.run(CR, RULE_SET);
        var orphan = new InMemoryImpactPorts.ReviewTaskLinkages();
        var reader = new ChangeImpactAnalysisService(changeRequests, specifications, id -> Optional.empty(),
                new RelevantProductDiscovery(products),
                new ImpactStrategyRegistry(List.of(strategy), EnumSet.of(ChangeType.INGREDIENT_SPEC)),
                new ImpactAnalysisApplicationService(runs, findings, orphan, audit),
                runs, findings, orphan, integration, Clock.fixed(NOW, ZoneOffset.UTC), () -> "x");

        assertThatIllegalStateException().isThrownBy(() -> reader.get(first.run().impactAnalysisRunId()))
                .withMessageContaining("has no review task");
    }

    private void submitted() {
        changeRequests.save(request(ChangeRequestStatus.SUBMITTED));
    }

    private void relevant(String... productIds) {
        for (String productId : productIds) {
            products.add(MATERIAL, new RelevantProduct(productId, "formula_" + productId + "_v2",
                    "label_" + productId + "_v1", List.of("fi_" + productId + "_1")));
        }
    }

    private void assertNothingWritten() {
        assertThat(runs.findByChangeRequestId(CR)).isEmpty();
        assertThat(tasks.saved()).isEmpty();
        assertThat(audits).isEmpty();
        changeRequests.findById(CR).ifPresent(request ->
                assertThat(request.status()).isNotEqualTo(ChangeRequestStatus.ANALYZED));
    }

    private static ChangeRequest request(ChangeRequestStatus status) {
        return new ChangeRequest(CR, "CR-1", ChangeType.INGREDIENT_SPEC, status, NOW, "user_change_manager",
                "Chocolate Base Spec V2 adds Soy Lecithin",
                new VersionChange("spec_chocolate_v1", "spec_chocolate_v2"), "prov_scenario_input");
    }

    private static SpecificationVersionFacts spec(String id, int version, Lifecycle lifecycle) {
        return new SpecificationVersionFacts(id, MATERIAL, version, lifecycle, LocalDate.of(2026, 1, 1));
    }

    private static RuleSetVersion ruleSet() {
        return new RuleSetVersion(RULE_SET, "US_FALCPA_DEMO", "1.0", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, true, "Demo", "prov_project_seed", List.of());
    }

    private static void assertFailure(ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ImpactFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(status);
            assertThat(failure.code()).isEqualTo(code);
        });
    }

    private static final class FakeIntegration implements ImpactIntegration {
        private final List<String> calls = new ArrayList<>();
        private boolean deny;
        private String assignee = "user_label_officer";

        @Override
        public String requireActor(Permission permission) {
            calls.add("requireActor:" + permission.name());
            if (deny) {
                throw new AuthorizationDeniedException("Actor lacks required permission: " + permission.code());
            }
            return "user_change_manager";
        }

        @Override
        public String authenticate() {
            calls.add("authenticate");
            return "user_auditor";
        }

        @Override
        public void auditChangeRequestCreated(String actorId, String changeRequestId, String dataProvenanceId) {
            throw new UnsupportedOperationException("Impact runs do not create change requests");
        }

        @Override
        public void auditImpactRun(
                String actorId, String changeRequestId, String impactAnalysisRunId, String dataProvenanceId) {
            throw new UnsupportedOperationException("The persistence service writes the impact audit event");
        }

        @Override
        public String reviewTaskAssignee() {
            calls.add("reviewTaskAssignee");
            if (assignee == null) {
                throw new IllegalStateException("No active user holds LABEL.CREATE to receive review tasks");
            }
            return assignee;
        }
    }
}
