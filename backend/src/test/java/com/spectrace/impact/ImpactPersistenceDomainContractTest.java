package com.spectrace.impact;

import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactPersistenceDomainContractTest {

    private static final Instant START = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void runRequiresCompletionTimeOnlyForTerminalStatusesAndNotBeforeStart() {
        assertThatThrownBy(() -> run(ImpactRunStatus.RUNNING, START.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(ImpactRunStatus.COMPLETED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(ImpactRunStatus.FAILED, START.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseEnumsRejectMissingAndUnsupportedValues() {
        assertThat(ImpactClassification.fromDatabase("REVIEW_REQUIRED"))
                .isEqualTo(ImpactClassification.REVIEW_REQUIRED);
        assertThat(ImpactRunStatus.fromDatabase("RUNNING")).isEqualTo(ImpactRunStatus.RUNNING);
        assertThat(ReviewTaskStatus.fromDatabase("OPEN")).isEqualTo(ReviewTaskStatus.OPEN);

        assertThatThrownBy(() -> ImpactClassification.fromDatabase(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ImpactClassification.fromDatabase("OTHER"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ImpactRunStatus.fromDatabase(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ImpactRunStatus.fromDatabase("OTHER")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReviewTaskStatus.fromDatabase(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReviewTaskStatus.fromDatabase("OTHER"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findingsRequireConsistentClassificationsAndDistinctMissingCodes() {
        ImpactFinding review = finding("formula-v2", ImpactClassification.REVIEW_REQUIRED, List.of("ZINC", "SOY"));
        assertThat(review.requiresReviewTask()).isTrue();
        assertThat(review.missingAllergenCodes()).containsExactly("SOY", "ZINC");
        assertThat(finding(null, ImpactClassification.NO_ACTION, List.of()).requiresReviewTask()).isFalse();

        assertThatThrownBy(() -> finding("formula-v1", ImpactClassification.REVIEW_REQUIRED, List.of("SOY")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finding(null, ImpactClassification.REVIEW_REQUIRED, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finding(null, ImpactClassification.NO_ACTION, List.of("SOY")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finding(null, ImpactClassification.REVIEW_REQUIRED, List.of("SOY", "SOY")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewTaskLinkageRequiresItsIdentifiers() {
        assertThatThrownBy(() -> new ReviewTaskLinkage(
                " ", "finding-1", "product-1", "label-1", null, ReviewTaskStatus.OPEN,
                "assignee-1", "creator-1", START, "provenance-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ImpactAnalysisRun run(ImpactRunStatus status, Instant completedAt) {
        return new ImpactAnalysisRun(
                "run-1", "RUN-1", "change-1", "rules-1", status,
                START, completedAt, "user-1", "provenance-1");
    }

    private ImpactFinding finding(
            String proposedFormulaVersionId,
            ImpactClassification classification,
            List<String> missingCodes
    ) {
        return new ImpactFinding(
                "finding-1", "run-1", "product-1", "formula-v1", proposedFormulaVersionId,
                "label-v1", classification, missingCodes, "explanation", "provenance-1");
    }
}
