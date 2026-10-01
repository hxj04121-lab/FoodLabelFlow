package com.spectrace.workflow.domain;

import org.springframework.stereotype.Component;

@Component
public class LabelTransitionPolicy {

    public void requireTransition(
            String currentStatus,
            String targetStatus
    ) {
        boolean allowed = switch (currentStatus) {
            case "DRAFT" ->
                    "PENDING_REVIEW".equals(targetStatus);

            case "PENDING_REVIEW" ->
                    "APPROVED".equals(targetStatus)
                            || "DRAFT".equals(targetStatus)
                            || "REJECTED".equals(targetStatus);

            case "APPROVED" ->
                    "PUBLISHED".equals(targetStatus);

            case "PUBLISHED" ->
                    "SUPERSEDED".equals(targetStatus);

            default -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid label lifecycle transition: "
                            + currentStatus
                            + " -> "
                            + targetStatus
            );
        }
    }

    public void requirePublishable(
            String lifecycleStatus
    ) {
        requireTransition(
                lifecycleStatus,
                "PUBLISHED"
        );
    }
}