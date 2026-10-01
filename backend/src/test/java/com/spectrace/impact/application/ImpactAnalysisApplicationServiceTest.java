package com.spectrace.impact.application;

import com.spectrace.audit.application.port.ImpactAuditEventPort;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ImpactAnalysisApplicationServiceTest {

    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private final ImpactAnalysisRunRepository runs = mock(ImpactAnalysisRunRepository.class);
    private final ImpactFindingRepository findings = mock(ImpactFindingRepository.class);
    private final ReviewTaskLinkageRepository tasks = mock(ReviewTaskLinkageRepository.class);
    private final ImpactAuditEventPort audit = mock(ImpactAuditEventPort.class);
    private final ImpactAnalysisApplicationService service =
            new ImpactAnalysisApplicationService(runs, findings, tasks, audit);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResults")
    void invalidProducerResultsFailBeforeAnyWrite(
            String scenario, List<ImpactFinding> result, List<ReviewTaskLinkage> links,
            Class<? extends RuntimeException> failureType) {
        ImpactAnalysisRun run = new ImpactAnalysisRun("run", "code-run", "change", "rules",
                ImpactRunStatus.COMPLETED, START, START.plusSeconds(1), "actor", "provenance");

        assertThatThrownBy(() -> service.execute(run, result, links)).isInstanceOf(failureType);
        verifyNoInteractions(runs, findings, tasks, audit);
    }

    private static Stream<Arguments> invalidResults() {
        ImpactFinding review = finding("run", ImpactClassification.REVIEW_REQUIRED);
        ImpactFinding noAction = finding("run", ImpactClassification.NO_ACTION);
        ReviewTaskLinkage validLink = link("task", "finding", "product", "label");
        return Stream.of(
                Arguments.of("finding belongs to another run",
                        List.of(finding("other-run", ImpactClassification.NO_ACTION)), List.of(),
                        IllegalArgumentException.class),
                Arguments.of("link has no finding", List.of(review),
                        List.of(link("task", "other-finding", "product", "label")), IllegalArgumentException.class),
                Arguments.of("NO_ACTION cannot create a task", List.of(noAction), List.of(validLink),
                        IllegalArgumentException.class),
                Arguments.of("task product differs from finding", List.of(review),
                        List.of(link("task", "finding", "other-product", "label")), IllegalArgumentException.class),
                Arguments.of("task current label differs from finding", List.of(review),
                        List.of(link("task", "finding", "product", "other-label")), IllegalArgumentException.class),
                Arguments.of("REVIEW_REQUIRED needs a task", List.of(review), List.of(),
                        IllegalArgumentException.class),
                Arguments.of("duplicate finding IDs", List.of(review, review), List.of(validLink),
                        IllegalStateException.class),
                Arguments.of("duplicate task links", List.of(review),
                        List.of(validLink, link("other-task", "finding", "product", "label")),
                        IllegalStateException.class));
    }

    private static ImpactFinding finding(String runId, ImpactClassification classification) {
        return new ImpactFinding("finding", runId, "product", "formula", null, "label", classification,
                classification.requiresReviewTask() ? List.of("SOY") : List.of(), "explanation", "provenance");
    }

    private static ReviewTaskLinkage link(String taskId, String findingId, String productId, String labelId) {
        return new ReviewTaskLinkage(taskId, findingId, productId, labelId, null, ReviewTaskStatus.OPEN,
                "assignee", "actor", START, "provenance");
    }
}
