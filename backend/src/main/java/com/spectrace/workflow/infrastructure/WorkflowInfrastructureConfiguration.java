package com.spectrace.workflow.infrastructure;

import com.spectrace.workflow.domain.LabelTransitionPolicy;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring wiring for workflow domain policies. */
@Configuration(proxyBeanMethods = false)
public class WorkflowInfrastructureConfiguration {

    @Bean
    public LabelTransitionPolicy labelTransitionPolicy() {
        return new LabelTransitionPolicy();
    }

    @Bean
    public MakerCheckerPolicy makerCheckerPolicy() {
        return new MakerCheckerPolicy();
    }
}
