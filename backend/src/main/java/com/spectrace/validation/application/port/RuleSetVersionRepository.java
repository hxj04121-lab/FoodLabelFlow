package com.spectrace.validation.application.port;

import com.spectrace.validation.domain.RuleSetVersion;

import java.util.Optional;

public interface RuleSetVersionRepository {

    Optional<RuleSetVersion> findById(String ruleSetVersionId);

    Optional<RuleSetVersion> findActiveById(String ruleSetVersionId);
}
