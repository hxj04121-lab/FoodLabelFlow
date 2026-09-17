package com.spectrace.validation.application.port;

import com.spectrace.validation.domain.RuleSetVersion;

import java.util.Optional;

public interface RuleSetVersionRepository {

    Optional<RuleSetVersion> findById(String ruleSetVersionId);

    /** Exact-ID lookup filtered by the domain lifecycle policy; never selects another version. */
    default Optional<RuleSetVersion> findActiveById(String ruleSetVersionId) {
        return findById(ruleSetVersionId)
                .filter(version -> version.lifecycleStatus().isExecutable());
    }
}
