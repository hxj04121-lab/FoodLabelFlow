package com.spectrace.impact.support;

import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import com.spectrace.impact.application.ImpactRunAlreadyExistsException;
import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskPort;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactFinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unit-test fakes for every impact port, so the impact core can be built before the
 * M2/M4/M5 adapters exist. Each fake enforces the matching V1/V2 key constraint.
 */
public final class InMemoryImpactPorts {
    private InMemoryImpactPorts() {
    }

    public static final class ChangeRequests implements ChangeRequestRepository {
        private final Map<String, ChangeRequest> byId = new LinkedHashMap<>();

        @Override
        public void save(ChangeRequest changeRequest) {
            Objects.requireNonNull(changeRequest, "changeRequest");
            boolean duplicateCode = byId.values().stream()
                    .anyMatch(saved -> saved.changeRequestCode().equals(changeRequest.changeRequestCode()));
            if (byId.containsKey(changeRequest.changeRequestId()) || duplicateCode) {
                throw new IllegalStateException("Duplicate change request " + changeRequest.changeRequestId());
            }
            byId.put(changeRequest.changeRequestId(), changeRequest);
        }

        @Override
        public Optional<ChangeRequest> findById(String changeRequestId) {
            return Optional.ofNullable(byId.get(changeRequestId));
        }

        @Override
        public Optional<ChangeRequest> findOpenByVersionChange(ChangeType changeType, VersionChange versionChange) {
            return byId.values().stream()
                    .filter(saved -> saved.changeType() == changeType
                            && saved.versionChange().equals(versionChange)
                            && saved.status() != ChangeRequestStatus.CANCELLED)
                    .findFirst();
        }

        @Override
        public List<ChangeRequest> findPage(
                ChangeType changeType, Set<ChangeRequestStatus> statuses, int limit, int offset) {
            return byId.values().stream()
                    .filter(saved -> saved.changeType() == changeType && statuses.contains(saved.status()))
                    .sorted(Comparator.comparing(ChangeRequest::changeRequestId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        public List<ChangeRequest> saved() {
            return List.copyOf(byId.values());
        }
    }

    public static final class Runs implements ImpactAnalysisRunRepository {
        private final Map<String, ImpactAnalysisRun> byId = new LinkedHashMap<>();

        @Override
        public void save(ImpactAnalysisRun run) {
            Objects.requireNonNull(run, "run");
            Optional<ImpactAnalysisRun> existing = byId.values().stream()
                    .filter(saved -> saved.changeRequestId().equals(run.changeRequestId()))
                    .findFirst();
            if (existing.isPresent()) {
                throw new ImpactRunAlreadyExistsException(existing.get(),
                        new IllegalStateException("Duplicate change request " + run.changeRequestId()));
            }
            boolean duplicateCode = byId.values().stream()
                    .anyMatch(saved -> saved.runCode().equals(run.runCode()));
            if (byId.containsKey(run.impactAnalysisRunId()) || duplicateCode) {
                throw new IllegalStateException("Duplicate impact run " + run.impactAnalysisRunId());
            }
            byId.put(run.impactAnalysisRunId(), run);
        }

        @Override
        public Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId) {
            return Optional.ofNullable(byId.get(impactAnalysisRunId));
        }

        @Override
        public List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId) {
            return byId.values().stream()
                    .filter(run -> run.changeRequestId().equals(changeRequestId))
                    .sorted(Comparator.comparing(ImpactAnalysisRun::startedAt)
                            .thenComparing(ImpactAnalysisRun::impactAnalysisRunId))
                    .toList();
        }
    }

    public static final class Findings implements ImpactFindingRepository {
        private final Map<String, ImpactFinding> byId = new LinkedHashMap<>();

        @Override
        public void saveAll(String impactAnalysisRunId, List<ImpactFinding> findings) {
            for (ImpactFinding finding : List.copyOf(findings)) {
                if (!finding.impactAnalysisRunId().equals(impactAnalysisRunId)) {
                    throw new IllegalArgumentException("Finding belongs to another run");
                }
                boolean duplicateProduct = byId.values().stream().anyMatch(saved ->
                        saved.impactAnalysisRunId().equals(impactAnalysisRunId)
                                && saved.productId().equals(finding.productId()));
                if (byId.containsKey(finding.impactFindingId()) || duplicateProduct) {
                    throw new IllegalStateException("Duplicate finding " + finding.impactFindingId());
                }
                byId.put(finding.impactFindingId(), finding);
            }
        }

        @Override
        public List<ImpactFinding> findByRunId(String impactAnalysisRunId) {
            return byId.values().stream()
                    .filter(finding -> finding.impactAnalysisRunId().equals(impactAnalysisRunId))
                    .sorted(Comparator.comparing(ImpactFinding::productId))
                    .toList();
        }
    }

    public static final class ReviewTasks implements ReviewTaskPort {
        private final Map<String, OpenReviewTask> byFindingId = new LinkedHashMap<>();

        @Override
        public String open(OpenReviewTask command) {
            String findingId = Objects.requireNonNull(command, "command").finding().impactFindingId();
            if (byFindingId.putIfAbsent(findingId, command) != null) {
                throw new IllegalStateException("ReviewTask already open for finding " + findingId);
            }
            return "review-task-" + byFindingId.size();
        }

        public List<OpenReviewTask> opened() {
            return List.copyOf(byFindingId.values());
        }
    }

    public static final class RelevantProducts implements RelevantProductLookupPort {
        private final Map<String, List<RelevantProduct>> byMaterial = new HashMap<>();

        public RelevantProducts add(String supplierMaterialId, RelevantProduct product) {
            byMaterial.computeIfAbsent(supplierMaterialId, ignored -> new ArrayList<>()).add(product);
            return this;
        }

        @Override
        public List<RelevantProduct> findProductsUsingMaterial(String supplierMaterialId) {
            return byMaterial.getOrDefault(supplierMaterialId, List.of()).stream()
                    .sorted(Comparator.comparing(RelevantProduct::productId))
                    .toList();
        }
    }

    public static final class SpecificationVersions implements SpecificationVersionLookupPort {
        private final Set<String> materials = new HashSet<>();
        private final Map<String, SpecificationVersionFacts> byId = new HashMap<>();
        private final List<String> locked = new ArrayList<>();

        public SpecificationVersions addMaterial(String supplierMaterialId) {
            materials.add(supplierMaterialId);
            return this;
        }

        /** Adds the version and its supplier material. */
        public SpecificationVersions add(SpecificationVersionFacts facts) {
            materials.add(facts.supplierMaterialId());
            byId.put(facts.specificationVersionId(), facts);
            return this;
        }

        @Override
        public boolean supplierMaterialExists(String supplierMaterialId) {
            return materials.contains(supplierMaterialId);
        }

        @Override
        public Optional<SpecificationVersionFacts> findById(String specificationVersionId) {
            return Optional.ofNullable(byId.get(specificationVersionId));
        }

        @Override
        public Optional<SpecificationVersionFacts> lockById(String specificationVersionId) {
            locked.add(specificationVersionId);
            return findById(specificationVersionId);
        }

        @Override
        public List<SpecificationVersionFacts> findAllById(Collection<String> specificationVersionIds) {
            return specificationVersionIds.stream().distinct()
                    .map(byId::get).filter(Objects::nonNull).toList();
        }

        public List<String> locked() {
            return List.copyOf(locked);
        }
    }
}
