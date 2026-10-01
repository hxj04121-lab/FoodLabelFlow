package com.spectrace.impact.application;

import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.Lifecycle;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.SpecificationVersionFacts;
import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.application.port.ImpactIntegration;
import com.spectrace.impact.application.port.ImpactIntegration.Permission;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Creates and reads INGREDIENT_SPEC change requests (SCRUM-76). */
public class ChangeRequestService {
    /** Seeded provenance for change requests entered for the class scenario. */
    public static final String CHANGE_REQUEST_PROVENANCE = "prov_scenario_input";

    private final ChangeRequestRepository changeRequests;
    private final SpecificationVersionLookupPort specifications;
    private final ImpactIntegration integration;
    private final Clock clock;

    public ChangeRequestService(
            ChangeRequestRepository changeRequests,
            SpecificationVersionLookupPort specifications,
            ImpactIntegration integration,
            Clock clock
    ) {
        this.changeRequests = Objects.requireNonNull(changeRequests, "changeRequests");
        this.specifications = Objects.requireNonNull(specifications, "specifications");
        this.integration = Objects.requireNonNull(integration, "integration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Check every business precondition before the first write, so no database CHECK
     * or foreign key can surface as a 500. The request, and its audit event in the same
     * transaction, commit together or not at all.
     */
    @Transactional
    public ChangeRequestView create(CreateIngredientSpecChange command) {
        Objects.requireNonNull(command, "command");
        String actorId = integration.requireActor(Permission.CREATE_CHANGE_REQUEST);
        if (command.previousSpecificationVersionId().equals(command.targetSpecificationVersionId())) {
            throw ImpactFailure.precondition("SPECIFICATION_VERSION_UNCHANGED",
                    "The target specification version must differ from the previous one");
        }
        String materialId = command.supplierMaterialId();
        // Unknown references in the body are 422; 404 is reserved for identifiers in the path.
        if (!specifications.supplierMaterialExists(materialId)) {
            throw missingReference("Supplier material " + materialId);
        }
        // Lock the target before the duplicate check so concurrent identical requests serialise.
        SpecificationVersionFacts target = specifications.lockById(command.targetSpecificationVersionId())
                .orElseThrow(() -> missingReference(
                        "Specification version " + command.targetSpecificationVersionId()));
        SpecificationVersionFacts previous = specifications.findById(command.previousSpecificationVersionId())
                .orElseThrow(() -> missingReference(
                        "Specification version " + command.previousSpecificationVersionId()));
        requireMaterial(materialId, previous);
        requireMaterial(materialId, target);
        if (previous.lifecycle() == Lifecycle.DRAFT) {
            throw ImpactFailure.precondition("SPECIFICATION_NOT_RELEASED",
                    "The previous specification version was never released");
        }
        if (target.lifecycle() != Lifecycle.RELEASED) {
            throw ImpactFailure.precondition("SPECIFICATION_NOT_RELEASED",
                    "The target specification version must be released");
        }
        if (!target.isEffectiveOn(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw ImpactFailure.precondition("SPECIFICATION_NOT_EFFECTIVE",
                    "The target specification version is not effective yet");
        }

        var versionChange = new VersionChange(previous.specificationVersionId(), target.specificationVersionId());
        if (changeRequests.findOpenByVersionChange(ChangeType.INGREDIENT_SPEC, versionChange).isPresent()) {
            throw ImpactFailure.conflict("A change request for this specification change already exists");
        }
        String changeRequestId = UUID.randomUUID().toString();
        var request = new ChangeRequest(
                changeRequestId,
                "CR-" + changeRequestId,
                ChangeType.INGREDIENT_SPEC,
                ChangeRequestStatus.SUBMITTED,
                // The canonical MySQL DATETIME column stores whole UTC seconds.
                clock.instant().truncatedTo(ChronoUnit.SECONDS),
                actorId,
                command.description(),
                versionChange,
                CHANGE_REQUEST_PROVENANCE);
        changeRequests.save(request);
        integration.auditChangeRequestCreated(actorId, changeRequestId, CHANGE_REQUEST_PROVENANCE);
        return new ChangeRequestView(request, materialId);
    }

    /** Only INGREDIENT_SPEC requests exist at this contract version; other types read as absent. */
    @Transactional(readOnly = true)
    public ChangeRequestView get(String changeRequestId) {
        integration.authenticate();
        ChangeRequest request = changeRequests.findById(changeRequestId)
                .filter(found -> found.changeType() == ChangeType.INGREDIENT_SPEC)
                .orElseThrow(() -> ImpactFailure.notFound("Change request " + changeRequestId + " was not found"));
        String materialId = specifications.findById(request.versionChange().toVersionId())
                .map(SpecificationVersionFacts::supplierMaterialId)
                .orElseThrow(() -> new IllegalStateException(
                        "Change request " + changeRequestId + " references a missing specification version"));
        return new ChangeRequestView(request, materialId);
    }

    private static void requireMaterial(String materialId, SpecificationVersionFacts specification) {
        if (!materialId.equals(specification.supplierMaterialId())) {
            throw ImpactFailure.precondition("SPECIFICATION_MATERIAL_MISMATCH",
                    "Specification version " + specification.specificationVersionId()
                            + " belongs to a different supplier material");
        }
    }

    private static ImpactFailure missingReference(String reference) {
        return ImpactFailure.precondition("CHANGE_REFERENCE_NOT_FOUND", reference + " was not found");
    }
}
