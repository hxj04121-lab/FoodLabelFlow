package com.spectrace.identity.interfaces.web;

import com.spectrace.identity.application.CurrentIdentityView;
import com.spectrace.identity.application.ExternalActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentIdentityController {
    private final ExternalActorResolver actors;

    public CurrentIdentityController(ExternalActorResolver actors) { this.actors = actors; }

    @GetMapping("/api/identity/current")
    public CurrentIdentityView current(HttpServletRequest request) {
        return CurrentIdentityView.from(actors.resolve(
                request.getHeader("X-Auth-Provider"), request.getHeader("X-External-Subject")));
    }
}
