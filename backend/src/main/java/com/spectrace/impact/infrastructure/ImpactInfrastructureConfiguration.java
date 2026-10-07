package com.spectrace.impact.infrastructure;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import com.spectrace.impact.application.ChangeImpactAnalysisService;
import com.spectrace.impact.application.ChangeRequestService;
import com.spectrace.impact.application.ImpactAnalysisApplicationService;
import com.spectrace.impact.application.RelevantProductDiscovery;
import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ImpactIntegration;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.application.strategy.ImpactStrategy;
import com.spectrace.impact.application.strategy.ImpactStrategyRegistry;
import com.spectrace.impact.application.strategy.IngredientSpecImpactStrategy;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

/** Spring wiring for the impact application services. */
@Configuration(proxyBeanMethods = false)
public class ImpactInfrastructureConfiguration {

    @Bean
    public ChangeRequestService changeRequestService(
            ChangeRequestRepository changeRequests,
            SpecificationVersionLookupPort specifications,
            ImpactIntegration integration
    ) {
        return new ChangeRequestService(changeRequests, specifications, integration, Clock.systemUTC());
    }

    @Bean
    public RelevantProductDiscovery relevantProductDiscovery(RelevantProductLookupPort lookup) {
        return new RelevantProductDiscovery(lookup);
    }

    @Bean
    public IngredientSpecImpactStrategy ingredientSpecImpactStrategy(
            FormulaCompositionPort formulas, LabelSnapshotPort labels, AllergenFactsPort allergens) {
        return new IngredientSpecImpactStrategy(formulas, labels, allergens);
    }

    /** FORMULA and RULE_SET stay extension points: no strategy, so no placeholder bean. */
    @Bean
    public ImpactStrategyRegistry impactStrategyRegistry(List<ImpactStrategy> strategies) {
        return new ImpactStrategyRegistry(strategies, EnumSet.of(ChangeType.INGREDIENT_SPEC));
    }

    @Bean
    public ChangeImpactAnalysisService changeImpactAnalysisService(
            ChangeRequestRepository changeRequests,
            SpecificationVersionLookupPort specifications,
            RuleSetVersionRepository ruleSets,
            RelevantProductDiscovery discovery,
            ImpactStrategyRegistry strategies,
            ImpactAnalysisApplicationService persistence,
            ImpactAnalysisRunRepository runs,
            ImpactFindingRepository findings,
            ReviewTaskLinkageRepository reviewTasks,
            ImpactIntegration integration
    ) {
        return new ChangeImpactAnalysisService(changeRequests, specifications, ruleSets, discovery, strategies,
                persistence, runs, findings, reviewTasks, integration, Clock.systemUTC(),
                ChangeImpactAnalysisService.randomIds());
    }
}
