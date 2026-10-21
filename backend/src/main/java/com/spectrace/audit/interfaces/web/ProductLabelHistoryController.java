package com.spectrace.audit.interfaces.web;

import com.spectrace.audit.application.ProductLabelHistory;
import com.spectrace.audit.application.ProductLabelHistoryReadService;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/products")
public class ProductLabelHistoryController {
    private final ProductLabelHistoryReadService history;
    private final ExternalActorResolver actors;
    private final AuthorizationService authorization;

    public ProductLabelHistoryController(ProductLabelHistoryReadService history,
                                         ExternalActorResolver actors,
                                         AuthorizationService authorization) {
        this.history = history;
        this.actors = actors;
        this.authorization = authorization;
    }

    @GetMapping("/{productId}/label-history")
    public ProductLabelHistory getProductLabelHistory(@PathVariable String productId, HttpServletRequest request) {
        AuthenticatedActor actor = actors.resolve(request.getHeader("X-Auth-Provider"),
                request.getHeader("X-External-Subject"));
        authorization.requirePermission(actor, "AUDIT.READ");
        return history.getProductLabelHistory(productId);
    }
}
