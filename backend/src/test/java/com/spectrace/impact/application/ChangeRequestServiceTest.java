package com.spectrace.impact.application;

import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.Lifecycle;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.SpecificationVersionFacts;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.impact.application.port.ImpactIntegration;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.support.InMemoryImpactPorts;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-29T08:15:30.500Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final String MATERIAL = "mat_chocolate_base";
    private static final String DESCRIPTION = "Chocolate Base Spec V2 adds Soy Lecithin";

    private final InMemoryImpactPorts.ChangeRequests changeRequests = new InMemoryImpactPorts.ChangeRequests();
    private final InMemoryImpactPorts.SpecificationVersions specifications = new InMemoryImpactPorts.SpecificationVersions()
            .add(spec("spec_chocolate_v1", MATERIAL, 1, Lifecycle.RETIRED, TODAY.minusMonths(9)))
            .add(spec("spec_chocolate_v2", MATERIAL, 2, Lifecycle.RELEASED, TODAY))
            .add(spec("spec_chocolate_v3", MATERIAL, 3, Lifecycle.DRAFT, TODAY))
            .add(spec("spec_chocolate_v4", MATERIAL, 4, Lifecycle.RELEASED, TODAY.plusDays(1)))
            .add(spec("spec_soy_carrier_v1", "mat_soy_carrier", 1, Lifecycle.RELEASED, TODAY));
    private final RecordingIntegration integration = new RecordingIntegration();
    private final ChangeRequestService service = new ChangeRequestService(
            changeRequests, specifications, integration, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsASubmittedIngredientSpecChangeAndAuditsItWithTheTrustedActor() {
        ChangeRequestView view = service.create(command("spec_chocolate_v1", "spec_chocolate_v2"));

        ChangeRequest saved = changeRequests.saved().getFirst();
        assertThat(view.changeRequest()).isEqualTo(saved);
        assertThat(view.supplierMaterialId()).isEqualTo(MATERIAL);
        assertThat(saved.changeType()).isEqualTo(ChangeType.INGREDIENT_SPEC);
        assertThat(saved.status()).isEqualTo(ChangeRequestStatus.SUBMITTED);
        assertThat(saved.versionChange()).isEqualTo(new VersionChange("spec_chocolate_v1", "spec_chocolate_v2"));
        assertThat(saved.requestedAt()).isEqualTo(Instant.parse("2026-09-29T08:15:30Z"));
        assertThat(saved.requestedByUserId()).isEqualTo("user_change_manager");
        assertThat(saved.changeRequestCode()).isEqualTo("CR-" + saved.changeRequestId());
        assertThat(saved.description()).isEqualTo(DESCRIPTION);
        assertThat(saved.dataProvenanceId()).isEqualTo(ChangeRequestService.CHANGE_REQUEST_PROVENANCE);
        assertThat(specifications.locked()).containsExactly("spec_chocolate_v2");
        assertThat(integration.calls).containsExactly(
                "requireActor:CREATE_CHANGE_REQUEST",
                "audit:user_change_manager:" + saved.changeRequestId() + ":prov_scenario_input");
    }

    @Test
    void permissionIsCheckedBeforeAnyLookupOrWrite() {
        integration.deny = true;

        assertThatThrownBy(() -> service.create(command("spec_chocolate_v1", "spec_chocolate_v2")))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThat(specifications.locked()).isEmpty();
        assertThat(changeRequests.saved()).isEmpty();
        assertThat(integration.calls).containsExactly("requireActor:CREATE_CHANGE_REQUEST");
    }

    @Test
    void identicalVersionsAreAnUnchangedSpecification() {
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v2")),
                422, "SPECIFICATION_VERSION_UNCHANGED");
        assertThat(specifications.locked()).isEmpty();
    }

    @Test
    void unknownBodyReferencesAre422NotPathNotFound() {
        assertFailure(() -> service.create(new CreateIngredientSpecChange(
                        "mat_unknown", "spec_chocolate_v1", "spec_chocolate_v2", DESCRIPTION)),
                422, "CHANGE_REFERENCE_NOT_FOUND");
        assertFailure(() -> service.create(command("spec_chocolate_v1", "spec_missing")),
                422, "CHANGE_REFERENCE_NOT_FOUND");
        assertFailure(() -> service.create(command("spec_missing", "spec_chocolate_v2")),
                422, "CHANGE_REFERENCE_NOT_FOUND");
    }

    @Test
    void descriptionIsRequiredAndCappedAtTheColumnLength() {
        String atLimit = "a".repeat(999) + "\uD83D\uDE00";
        assertThat(new CreateIngredientSpecChange(MATERIAL, "spec-1", "spec-2", atLimit).description())
                .isEqualTo(atLimit);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CreateIngredientSpecChange(MATERIAL, "spec-1", "spec-2", atLimit + "a"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CreateIngredientSpecChange(MATERIAL, "spec-1", "spec-2", " "));
    }

    @Test
    void everyVersionMustBelongToTheRequestedMaterial() {
        assertFailure(() -> service.create(command("spec_soy_carrier_v1", "spec_chocolate_v2")),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        assertFailure(() -> service.create(command("spec_chocolate_v1", "spec_soy_carrier_v1")),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        assertFailure(() -> service.create(new CreateIngredientSpecChange(
                        "mat_soy_carrier", "spec_chocolate_v1", "spec_chocolate_v2", DESCRIPTION)),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
    }

    @Test
    void targetMustBeReleasedAndPreviousMustHaveBeenReleased() {
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v3")),
                422, "SPECIFICATION_NOT_RELEASED");
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v1")),
                422, "SPECIFICATION_NOT_RELEASED");
        assertFailure(() -> service.create(command("spec_chocolate_v3", "spec_chocolate_v2")),
                422, "SPECIFICATION_NOT_RELEASED");
    }

    @Test
    void targetMustAlreadyBeEffective() {
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v4")),
                422, "SPECIFICATION_NOT_EFFECTIVE");
        // Effective from today is accepted.
        assertThat(service.create(command("spec_chocolate_v1", "spec_chocolate_v2")).changeRequest().status())
                .isEqualTo(ChangeRequestStatus.SUBMITTED);
    }

    @Test
    void anOpenRequestForTheSameChangeIsAConflict() {
        service.create(command("spec_chocolate_v1", "spec_chocolate_v2"));

        assertFailure(() -> service.create(command("spec_chocolate_v1", "spec_chocolate_v2")),
                409, "DATA_CONFLICT");
        assertThat(changeRequests.saved()).hasSize(1);
    }

    @Test
    void failedPreconditionsWriteNothing() {
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v4")),
                422, "SPECIFICATION_NOT_EFFECTIVE");

        assertThat(changeRequests.saved()).isEmpty();
        assertThat(integration.calls).noneMatch(call -> call.startsWith("audit:"));
    }

    @Test
    void readsAuthenticateAndReturnTheSpecificationMaterial() {
        String id = service.create(command("spec_chocolate_v1", "spec_chocolate_v2")).changeRequest().changeRequestId();
        integration.calls.clear();

        ChangeRequestView view = service.get(id);

        assertThat(view.changeRequest().changeRequestId()).isEqualTo(id);
        assertThat(view.supplierMaterialId()).isEqualTo(MATERIAL);
        assertThat(integration.calls).containsExactly("authenticate");
        assertFailure(() -> service.get("missing"), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void otherChangeTypesAreNotReadableThroughTheIngredientSpecResource() {
        changeRequests.save(new ChangeRequest("cr-formula", "CR-FORMULA", ChangeType.FORMULA,
                ChangeRequestStatus.SUBMITTED, NOW, "user_admin", "formula change",
                new VersionChange("formula-1", "formula-2"), "prov_scenario_input"));

        assertFailure(() -> service.get("cr-formula"), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void aDanglingSpecificationReferenceIsAServerFault() {
        changeRequests.save(new ChangeRequest("cr-dangling", "CR-DANGLING", ChangeType.INGREDIENT_SPEC,
                ChangeRequestStatus.SUBMITTED, NOW, "user_admin", "dangling",
                new VersionChange("spec_chocolate_v1", "spec_gone"), "prov_scenario_input"));

        assertThatIllegalStateException().isThrownBy(() -> service.get("cr-dangling"));
    }

    @Test
    void listFiltersBeforePagingAndOrdersByChangeRequestId() {
        saveRequest("cr-c", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.COMPLETED, "spec_chocolate_v2");
        saveRequest("cr-a", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED, "spec_chocolate_v2");
        saveRequest("cr-b", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.DRAFT, "spec_chocolate_v2");
        saveRequest("cr-d", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.CANCELLED, "spec_chocolate_v2");
        saveRequest("cr-e", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.ANALYZED, "spec_soy_carrier_v1");
        changeRequests.save(new ChangeRequest("cr-0-formula", "CR-FORMULA", ChangeType.FORMULA,
                ChangeRequestStatus.SUBMITTED, NOW, "user_admin", "formula change",
                new VersionChange("formula-1", "formula-2"), "prov_scenario_input"));

        assertThat(ids(service.list(50, 0))).containsExactly("cr-a", "cr-c", "cr-e");
        assertThat(ids(service.list(2, 0))).containsExactly("cr-a", "cr-c");
        assertThat(ids(service.list(2, 2))).containsExactly("cr-e");
        assertThat(service.list(1, 3)).isEmpty();
        assertThat(service.list(100, Integer.MAX_VALUE)).isEmpty();
        assertThat(service.list(50, 0)).extracting(ChangeRequestView::supplierMaterialId)
                .containsExactly(MATERIAL, MATERIAL, "mat_soy_carrier");
        assertThat(integration.calls).containsOnly("authenticate");
    }

    @Test
    void listBoundsAreCheckedBeforeAuthentication() {
        assertFailure(() -> service.list(0, 0), 400, "INVALID_REQUEST");
        assertFailure(() -> service.list(101, 0), 400, "INVALID_REQUEST");
        assertFailure(() -> service.list(50, -1), 400, "INVALID_REQUEST");
        assertThat(integration.calls).isEmpty();
        assertThat(service.list(100, 0)).isEmpty();
    }

    @Test
    void aListedRowWithAMissingSpecificationFailsTheWholeRead() {
        saveRequest("cr-a", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED, "spec_chocolate_v2");
        saveRequest("cr-b", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED, "spec_gone");

        assertThatIllegalStateException().isThrownBy(() -> service.list(50, 0)).withMessageContaining("cr-b");
    }

    private void saveRequest(String id, ChangeType type, ChangeRequestStatus status, String target) {
        changeRequests.save(new ChangeRequest(id, "CR-" + id, type, status, NOW, "user_admin", DESCRIPTION,
                new VersionChange("spec_chocolate_v1", target), "prov_scenario_input"));
    }

    private static List<String> ids(List<ChangeRequestView> views) {
        return views.stream().map(view -> view.changeRequest().changeRequestId()).toList();
    }

    private static CreateIngredientSpecChange command(String previous, String target) {
        return new CreateIngredientSpecChange(MATERIAL, previous, target, DESCRIPTION);
    }

    private static SpecificationVersionFacts spec(
            String id, String material, int version, Lifecycle lifecycle, LocalDate effectiveDate) {
        return new SpecificationVersionFacts(id, material, version, lifecycle, effectiveDate);
    }

    private static void assertFailure(ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ImpactFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(status);
            assertThat(failure.code()).isEqualTo(code);
            assertThat(failure.getMessage()).isNotBlank();
        });
    }

    private static final class RecordingIntegration implements ImpactIntegration {
        private final List<String> calls = new ArrayList<>();
        private boolean deny;

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
            calls.add("audit:" + actorId + ":" + changeRequestId + ":" + dataProvenanceId);
        }

        @Override
        public void auditImpactRun(
                String actorId, String changeRequestId, String impactAnalysisRunId, String dataProvenanceId) {
            throw new UnsupportedOperationException("Change requests do not audit impact runs");
        }
    }
}
