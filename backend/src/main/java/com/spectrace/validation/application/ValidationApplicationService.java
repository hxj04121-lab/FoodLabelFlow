package com.spectrace.validation.application;

import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.audit.application.port.AuditEventPort;
import com.spectrace.identity.application.port.AuthorizationPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationResultRepository;
import com.spectrace.validation.application.port.ValidationRunRepository;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationResult;
import com.spectrace.validation.domain.ValidationRun;
import com.spectrace.validation.domain.ValidationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ValidationApplicationService {

    public static final String VALIDATE_PERMISSION = "LABEL.VALIDATE";
    private static final String VALIDATION_PROVENANCE = "prov_validation_fixture";

    private final AuthorizationPort authorization;
    private final LabelSnapshotPort labelSnapshots;
    private final AllergenFactsPort allergenFacts;
    private final RuleSetVersionRepository ruleSets;
    private final ValidationRunRepository runs;
    private final ValidationResultRepository results;
    private final AuditEventPort audit;

    public ValidationApplicationService(
            AuthorizationPort authorization,
            LabelSnapshotPort labelSnapshots,
            AllergenFactsPort allergenFacts,
            RuleSetVersionRepository ruleSets,
            ValidationRunRepository runs,
            ValidationResultRepository results,
            AuditEventPort audit
    ) {
        this.authorization = authorization;
        this.labelSnapshots = labelSnapshots;
        this.allergenFacts = allergenFacts;
        this.ruleSets = ruleSets;
        this.runs = runs;
        this.results = results;
        this.audit = audit;
    }

    @Transactional
    public ValidationRunResponse validate(String labelVersionId, ValidationRunRequest request) {
        var actor = authorization.require(VALIDATE_PERMISSION);
        String requestedRuleSetId = requiredRuleSetId(request);
        LabelValidationSnapshot label = labelSnapshots.findValidationSnapshot(labelVersionId)
                .orElseThrow(() -> new ValidationFailure(
                        404, "RESOURCE_NOT_FOUND", "The requested label version was not found"));
        if (!label.current()) {
            throw new ValidationFailure(
                    409, "LABEL_VERSION_NOT_CURRENT", "Validation is permitted only for the current label version");
        }

        RuleSetVersion ruleSet = ruleSets.findById(requestedRuleSetId)
                .orElseThrow(() -> precondition("The requested rule-set version is not available"));
        if (ruleSet.lifecycleStatus() != RuleSetLifecycleStatus.ACTIVE) {
            throw precondition("Only an ACTIVE rule-set version can be used for validation");
        }

        Map<String, AllergenFact> factsById = allergenFacts.findByIds(label.declaredAllergenIds()).stream()
                .collect(Collectors.toUnmodifiableMap(AllergenFact::allergenId, Function.identity()));
        String runId = UUID.randomUUID().toString();
        List<ValidationResult> validationResults = evaluate(runId, label, ruleSet, factsById);
        boolean failed = validationResults.stream().anyMatch(ValidationResult::blocking);
        ValidationRun run = new ValidationRun(
                runId,
                label.labelVersionId(),
                requestedRuleSetId,
                failed ? ValidationStatus.FAILED : ValidationStatus.PASSED,
                actor.userId(),
                Instant.now(),
                failed ? "One or more blocking validation rules failed" : "All validation rules passed",
                VALIDATION_PROVENANCE
        );

        runs.save(run);
        results.saveAll(runId, validationResults);
        audit.recordValidationEvent(
                actor.userId(),
                label.labelVersionId(),
                runId,
                requestedRuleSetId,
                run.status().name(),
                VALIDATION_PROVENANCE
        );
        return ValidationRunResponse.from(run, validationResults);
    }

    @Transactional(readOnly = true)
    public ValidationRunResponse get(String validationRunId) {
        authorization.require(VALIDATE_PERMISSION);
        ValidationRun run = runs.findById(validationRunId)
                .orElseThrow(() -> new ValidationFailure(
                        404, "RESOURCE_NOT_FOUND", "The requested validation run was not found"));
        return ValidationRunResponse.from(run, results.findByRunId(validationRunId));
    }

    private List<ValidationResult> evaluate(
            String runId,
            LabelValidationSnapshot label,
            RuleSetVersion ruleSet,
            Map<String, AllergenFact> factsById
    ) {
        String ingredients = label.rawIngredientText().toLowerCase(java.util.Locale.ROOT);
        return ruleSet.ruleDefinitions().stream()
                .filter(RuleDefinition::active)
                .map(rule -> evaluateRule(runId, label, ingredients, rule, factsById))
                .toList();
    }

    private ValidationResult evaluateRule(
            String runId,
            LabelValidationSnapshot label,
            String ingredients,
            RuleDefinition rule,
            Map<String, AllergenFact> factsById
    ) {
        boolean triggered = Pattern.compile(rule.patternText(), Pattern.CASE_INSENSITIVE)
                .matcher(ingredients)
                .find();
        boolean passed;
        String message;
        if (rule.ruleType() == RuleType.INGREDIENT_TO_ALLERGEN) {
            passed = !triggered || label.declaredAllergenIds().contains(rule.targetAllergenId());
            AllergenFact fact = factsById.get(rule.targetAllergenId());
            String allergen = fact == null ? rule.targetAllergenId() : fact.allergenCode();
            message = triggered
                    ? (passed ? allergen + " declaration is present" : allergen + " declaration is missing")
                    : "Ingredient pattern is not present";
        } else {
            passed = !label.declaredAllergenIds().isEmpty();
            message = passed ? "Structured allergen declarations are present" : "Structured allergen declaration is missing";
        }
        return new ValidationResult(
                UUID.randomUUID().toString(),
                runId,
                rule.ruleDefinitionId(),
                rule.ruleCode() + (passed ? "_PASSED" : "_FAILED"),
                rule.severity(),
                passed,
                !passed && rule.severity().name().equals("ERROR"),
                message
        );
    }

    private static String requiredRuleSetId(ValidationRunRequest request) {
        if (request == null || request.ruleSetVersionId() == null || request.ruleSetVersionId().isBlank()) {
            throw new ValidationFailure(400, "INVALID_REQUEST", "ruleSetVersionId must be supplied");
        }
        return request.ruleSetVersionId();
    }

    private static ValidationFailure precondition(String message) {
        return new ValidationFailure(422, "VALIDATION_PRECONDITION_FAILED", message);
    }
}
