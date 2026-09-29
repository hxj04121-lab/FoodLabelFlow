package com.spectrace.impact.infrastructure;

import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import com.spectrace.impact.application.ChangeRequestService;
import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.application.port.ImpactIntegration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
}
