package com.spectrace.workflow.application.port;

import java.util.Optional;

public interface LabelWorkflowRepository {

    Optional<String> findCreatorUserId(
            String labelVersionId
    );

    Optional<LabelWorkflowVersion> findVersion(
            String labelVersionId
    );

    record LabelWorkflowVersion(
            String labelVersionId,
            String lifecycleStatus,
            boolean current
    ) {
    }
}