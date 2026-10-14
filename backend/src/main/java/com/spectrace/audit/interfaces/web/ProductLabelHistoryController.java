package com.spectrace.audit.interfaces.web;

import com.spectrace.audit.application.ProductLabelHistoryService;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.ExternalActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductLabelHistoryController {
    private final ProductLabelHistoryService history;
    private final ExternalActorResolver actors;
    private final AuthorizationService authorization;

    public ProductLabelHistoryController(ProductLabelHistoryService history, ExternalActorResolver actors,
                                         AuthorizationService authorization) {
        this.history = history;
        this.actors = actors;
        this.authorization = authorization;
    }

    @GetMapping("/api/audit/products/{productId}/label-history")
    public ProductLabelHistoryService.ProductLabelHistory get(@PathVariable String productId,
                                                               HttpServletRequest request) {
        var actor = actors.resolve(request.getHeader("X-Auth-Provider"), request.getHeader("X-External-Subject"));
        authorization.requirePermission(actor, "AUDIT.READ");
        return history.get(productId);
    }
}
