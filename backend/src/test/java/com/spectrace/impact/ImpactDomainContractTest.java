package com.spectrace.impact;

import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactDomainContractTest {
    private static final Instant STARTED = Instant.parse("2026-09-28T01:00:00Z");

    @Test
    void enumsUseExactlyTheV2DatabaseTokens() throws IOException {
        String constraints = migration("V2__constraints_indexes.sql");

        assertThat(names(ChangeType.values()))
                .containsExactlyElementsOf(checkTokens(constraints, "chk_change_request_type_v3"));
        assertThat(names(ChangeRequestStatus.values()))
                .containsExactlyElementsOf(checkTokens(constraints, "chk_change_request_status_v3"));
        assertThat(names(ImpactRunStatus.values()))
                .containsExactlyElementsOf(checkTokens(constraints, "chk_impact_run_status_v3"));
        assertThat(names(ImpactClassification.values()))
                .containsExactlyElementsOf(checkTokens(constraints, "chk_impact_finding_classification_v3"));
    }

    @Test
    void unknownOrMissingDatabaseTokensAreNeverDefaulted() {
        assertThat(ChangeType.fromDatabase("INGREDIENT_SPEC")).isEqualTo(ChangeType.INGREDIENT_SPEC);
        assertThat(ChangeRequestStatus.fromDatabase("ANALYZED")).isEqualTo(ChangeRequestStatus.ANALYZED);
        assertThat(ImpactRunStatus.fromDatabase("COMPLETED")).isEqualTo(ImpactRunStatus.COMPLETED);
        assertThat(ImpactClassification.fromDatabase("NO_ACTION")).isEqualTo(ImpactClassification.NO_ACTION);

        assertThatIllegalStateException().isThrownBy(() -> ChangeType.fromDatabase("LABEL"));
        assertThatIllegalStateException().isThrownBy(() -> ChangeRequestStatus.fromDatabase(" "));
        assertThatIllegalStateException().isThrownBy(() -> ImpactRunStatus.fromDatabase(null));
        assertThatIllegalStateException().isThrownBy(() -> ImpactClassification.fromDatabase("MAYBE"));
    }

    @Test
    void changeRequestCarriesOneTypedPairOfDifferentVersions() {
        ChangeRequest request = changeRequest(new VersionChange("spec_chocolate_v1", "spec_chocolate_v2"));

        assertThat(request.changeType()).isEqualTo(ChangeType.INGREDIENT_SPEC);
        assertThat(request.versionChange().toVersionId()).isEqualTo("spec_chocolate_v2");
        assertThatIllegalArgumentException().isThrownBy(() -> new VersionChange("spec-1", "spec-1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new VersionChange(" ", "spec-2"));
        assertThatIllegalArgumentException().isThrownBy(() -> new VersionChange("spec-1", null));
        assertThatNullPointerException().isThrownBy(() -> changeRequest(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChangeRequest(
                "cr-1", "CR-1", ChangeType.INGREDIENT_SPEC, ChangeRequestStatus.SUBMITTED, STARTED,
                "user-1", " ", new VersionChange("spec-1", "spec-2"), "prov-1"));
    }

    @Test
    void completionTimeIsPresentExactlyForTerminalRuns() {
        assertThat(run(ImpactRunStatus.RUNNING, null).completedAt()).isNull();
        assertThat(run(ImpactRunStatus.COMPLETED, STARTED).completedAt()).isEqualTo(STARTED);
        assertThat(run(ImpactRunStatus.FAILED, STARTED.plusSeconds(5)).status().isTerminal()).isTrue();
        assertThat(ImpactRunStatus.QUEUED.isTerminal()).isFalse();

        assertThatIllegalArgumentException().isThrownBy(() -> run(ImpactRunStatus.COMPLETED, null));
        assertThatIllegalArgumentException().isThrownBy(() -> run(ImpactRunStatus.QUEUED, STARTED));
        assertThatIllegalArgumentException().isThrownBy(() -> run(ImpactRunStatus.FAILED, STARTED.minusSeconds(1)));
    }

    @Test
    void classificationMustAgreeWithMissingAllergens() {
        ImpactFinding reviewRequired = finding(ImpactClassification.REVIEW_REQUIRED, List.of("SOY"));
        ImpactFinding noAction = finding(ImpactClassification.NO_ACTION, List.of());

        assertThat(reviewRequired.requiresReviewTask()).isTrue();
        assertThat(noAction.requiresReviewTask()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() ->
                finding(ImpactClassification.REVIEW_REQUIRED, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                finding(ImpactClassification.NO_ACTION, List.of("SOY")));
        assertThatNullPointerException().isThrownBy(() ->
                finding(ImpactClassification.NO_ACTION, null));
    }

    @Test
    void missingAllergenCodesAreSortedCopiedAndNeverDuplicated() {
        var codes = new ArrayList<>(List.of("SOY", "MILK"));
        ImpactFinding finding = finding(ImpactClassification.REVIEW_REQUIRED, codes);
        codes.clear();

        assertThat(finding.missingAllergenCodes()).containsExactly("MILK", "SOY");
        assertThatThrownBy(() -> finding.missingAllergenCodes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() ->
                finding(ImpactClassification.REVIEW_REQUIRED, List.of("SOY", "SOY")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                finding(ImpactClassification.REVIEW_REQUIRED, Arrays.asList("SOY", null)));
    }

    @Test
    void proposedFormulaIsOptionalButNeverTheCurrentFormula() {
        assertThat(finding("formula_1_v1", null).proposedFormulaVersionId()).isNull();
        assertThat(finding("formula_1_v1", "formula_1_v2").proposedFormulaVersionId()).isEqualTo("formula_1_v2");
        assertThatIllegalArgumentException().isThrownBy(() -> finding("formula_1_v1", "formula_1_v1"));
        assertThatIllegalArgumentException().isThrownBy(() -> finding("formula_1_v1", " "));
    }

    static ChangeRequest changeRequest(VersionChange versionChange) {
        return new ChangeRequest("cr-1", "CR-SOY-1", ChangeType.INGREDIENT_SPEC,
                ChangeRequestStatus.SUBMITTED, STARTED, "user-1",
                "Chocolate Base Spec V2 adds Soy Lecithin", versionChange, "prov-1");
    }

    static ImpactAnalysisRun run(ImpactRunStatus status, Instant completedAt) {
        return new ImpactAnalysisRun("run-1", "RUN-1", "cr-1", "ruleset-1", status,
                STARTED, completedAt, "user-1", "prov-1");
    }

    static ImpactFinding finding(ImpactClassification classification, List<String> missingCodes) {
        return new ImpactFinding("finding-1", "run-1", "product-1", "formula_1_v1", "formula_1_v2",
                "label-1", classification, missingCodes, "explanation", "prov-1");
    }

    private static ImpactFinding finding(String currentFormula, String proposedFormula) {
        return new ImpactFinding("finding-1", "run-1", "product-1", currentFormula, proposedFormula,
                "label-1", ImpactClassification.NO_ACTION, List.of(), "explanation", "prov-1");
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static List<String> checkTokens(String sql, String constraintName) {
        var matcher = Pattern.compile(constraintName + " CHECK \\(\\w+ IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(matcher.find()).as(constraintName).isTrue();
        return Arrays.stream(matcher.group(1).split(","))
                .map(token -> token.trim().replace("'", ""))
                .toList();
    }

    static String migration(String fileName) throws IOException {
        try (InputStream input = ImpactDomainContractTest.class.getResourceAsStream("/db/migration/" + fileName)) {
            return new String(Objects.requireNonNull(input, fileName).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
