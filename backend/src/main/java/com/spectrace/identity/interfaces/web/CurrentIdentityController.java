package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.CurrentIdentityView;
import com.spectrace.identity.application.ExternalActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CurrentIdentityController {
    private final ExternalActorResolver actors;

    public CurrentIdentityController(ExternalActorResolver actors) { this.actors = actors; }

    @GetMapping("/api/identity/current")
    public CurrentIdentityView current(HttpServletRequest request) {
        return CurrentIdentityView.from(actors.resolve(
                request.getHeader("X-Auth-Provider"), request.getHeader("X-External-Subject")));
    }

    @GetMapping("/api/identity/demo-options")
    public List<DemoIdentityOption> demoOptions() {
        if (!actors.isDemoIdentitySwitchingEnabled()) {
            throw new com.spectrace.identity.application.UnknownIdentityException(
                    "Development identity switching is disabled");
        }
        return List.of(
                option("MAKER", "dev-external-label-officer"),
                option("CHECKER", "dev-external-qa-approver"),
                option("PUBLISHER", "dev-external-publisher"));
    }

    private DemoIdentityOption option(String key, String subject) {
        CurrentIdentityView view = CurrentIdentityView.from(actors.resolve("DEV_EXTERNAL", subject));
        return new DemoIdentityOption(key, subject, view);
    }

    public record DemoIdentityOption(String key, String subject, CurrentIdentityView actor) {}
}
