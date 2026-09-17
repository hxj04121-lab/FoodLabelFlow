package com.spectrace.validation.infrastructure;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.rule.IngredientToAllergenEvaluator;
import com.spectrace.validation.application.rule.LabelDeclarationEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluatorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Spring wiring for the application-owned validation strategies and flow. */
@Configuration(proxyBeanMethods = false)
public class ValidationInfrastructureConfiguration {
    @Bean
    public IngredientToAllergenEvaluator ingredientToAllergenEvaluator() {
        return new IngredientToAllergenEvaluator();
    }

    @Bean
    public LabelDeclarationEvaluator labelDeclarationEvaluator() {
        return new LabelDeclarationEvaluator();
    }

    @Bean
    public RuleEvaluatorRegistry ruleEvaluatorRegistry(List<RuleEvaluator> evaluators) {
        return new RuleEvaluatorRegistry(evaluators);
    }

    @Bean
    public ValidationOrchestrator validationOrchestrator(
            LabelSnapshotPort labelSnapshots,
            FormulaCompositionPort formulaCompositions,
            AllergenFactsPort allergenFacts,
            RuleSetVersionRepository ruleSets,
            RuleEvaluatorRegistry evaluators,
            ValidationIntegration validationIntegration
    ) {
        return new ValidationOrchestrator(
                labelSnapshots, formulaCompositions, allergenFacts, ruleSets,
                evaluators, validationIntegration);
    }
}
