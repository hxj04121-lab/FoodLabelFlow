package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactFinding;

import java.time.Instant;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Workflow-owned ReviewTask creation: lifecycle by M4 (SCRUM-50), persistence by M5 (SCRUM-51). */
public interface ReviewTaskPort {

    /**
     * Open one OPEN task in the caller's transaction (MANDATORY) and return its ID.
     * The draft label reference (SCRUM-47 target_label_version_id, V1
     * review_task.draft_label_version_id) stays null until M4 links the replacement
     * draft. A second task for the same finding must fail, never be created.
     */
    String open(OpenReviewTask command);

    /** Holds the finding itself, so a NO_ACTION finding cannot reach the port. */
    record OpenReviewTask(
            ImpactFinding finding,
            String assignedToUserId,
            String createdByUserId,
            Instant createdAt
    ) {
        public OpenReviewTask {
            finding = Objects.requireNonNull(finding, "finding");
            if (!finding.requiresReviewTask()) {
                throw new IllegalArgumentException("A NO_ACTION finding never opens a ReviewTask");
            }
            assignedToUserId = requiredText(assignedToUserId, "assignedToUserId");
            createdByUserId = requiredText(createdByUserId, "createdByUserId");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
