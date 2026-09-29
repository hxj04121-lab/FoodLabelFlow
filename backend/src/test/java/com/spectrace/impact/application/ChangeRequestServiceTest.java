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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-29T08:15:30.500Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final String MATERIAL = "mat_chocolate_base";

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
        assertThat(saved.description()).contains(MATERIAL, "spec_chocolate_v1", "spec_chocolate_v2");
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
    void identicalVersionsAreAnInvalidRequest() {
        assertFailure(() -> service.create(command("spec_chocolate_v2", "spec_chocolate_v2")),
                400, "INVALID_REQUEST");
    }

    @Test
    void unknownMaterialOrSpecificationVersionIsNotFound() {
        assertFailure(() -> service.create(new CreateIngredientSpecChange(
                "mat_unknown", "spec_chocolate_v1", "spec_chocolate_v2")), 404, "RESOURCE_NOT_FOUND");
        assertFailure(() -> service.create(command("spec_chocolate_v1", "spec_missing")),
                404, "RESOURCE_NOT_FOUND");
        assertFailure(() -> service.create(command("spec_missing", "spec_chocolate_v2")),
                404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void everyVersionMustBelongToTheRequestedMaterial() {
        assertFailure(() -> service.create(command("spec_soy_carrier_v1", "spec_chocolate_v2")),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        assertFailure(() -> service.create(command("spec_chocolate_v1", "spec_soy_carrier_v1")),
                422, "SPECIFICATION_MATERIAL_MISMATCH");
        assertFailure(() -> service.create(new CreateIngredientSpecChange(
                        "mat_soy_carrier", "spec_chocolate_v1", "spec_chocolate_v2")),
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

    private static CreateIngredientSpecChange command(String previous, String target) {
        return new CreateIngredientSpecChange(MATERIAL, previous, target);
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
