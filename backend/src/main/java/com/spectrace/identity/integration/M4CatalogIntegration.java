package com.spectrace.identity.integration;

import com.spectrace.catalog.application.CatalogIntegration;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.identity.interfaces.web.RequestIdentityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class M4CatalogIntegration implements CatalogIntegration {

    private final RequestIdentityContext requestIdentityContext;
    private final IdentityService identityService;
    private final AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;

    public M4CatalogIntegration(
            RequestIdentityContext requestIdentityContext,
            IdentityService identityService,
            AuthorizationService authorizationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.requestIdentityContext = requestIdentityContext;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String requireActor(String permission) {
        if (!requestIdentityContext.isPresent()) {
            throw new IllegalArgumentException(
                    "Authenticated external identity headers are required"
            );
        }

        AuthenticatedActor actor = identityService.authenticate(
                requestIdentityContext.authProvider(),
                requestIdentityContext.externalSubject()
        );

        authorizationService.requirePermission(actor, permission);

        return actor.userId();
    }

    @Override
    public void audit(
            String actorId,
            String action,
            String entityId,
            String provenanceId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_event (
                    audit_event_id,
                    event_type,
                    entity_type,
                    entity_id,
                    event_at,
                    actor_user_id,
                    before_value,
                    after_value,
                    event_payload,
                    correlation_id,
                    data_provenance_id
                )
                VALUES (?, ?, 'CATALOG', ?, NOW(), ?, NULL, NULL,
                        JSON_OBJECT('source', 'M4CatalogIntegration'),
                        ?, ?)
                """,
                "audit_catalog_" + UUID.randomUUID().toString().replace("-", ""),
                action,
                entityId,
                actorId,
                entityId,
                provenanceId
        );
    }
}