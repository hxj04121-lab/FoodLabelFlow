package com.spectrace.audit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditApplicationService {

    private final JdbcTemplate jdbcTemplate;

    public AuditApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCatalogEvent(
            String actorId,
            String action,
            String entityId,
            String provenanceId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_event(
                  audit_event_id, event_type, entity_type, entity_id, event_at,
                  actor_user_id, event_payload, data_provenance_id
                ) VALUES (?, ?, 'CATALOG', ?, UTC_TIMESTAMP(), ?, JSON_OBJECT(), ?)
                """,
                UUID.randomUUID().toString(),
                action,
                entityId,
                actorId,
                provenanceId
        );
    }
}
