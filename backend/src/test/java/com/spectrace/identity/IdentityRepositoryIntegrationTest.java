package com.spectrace.identity;

import com.spectrace.identity.application.port.IdentityRepository;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityRepositoryIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    void mapsSeededExternalIdentityToRbacActor() {
        AuthenticatedActor actor = identityRepository
                .findActiveActorByExternalSubject(
                        "DEV_EXTERNAL",
                        "dev-external-qa-approver"
                )
                .orElseThrow();

        assertEquals("user_approver", actor.userId());
        assertEquals("qa.approver", actor.username());

        assertTrue(actor.roles().contains("APPROVER"));
        assertTrue(actor.permissions().contains("LABEL.APPROVE"));
        assertTrue(actor.permissions().contains("LABEL.REJECT"));
        assertTrue(actor.permissions().contains("LABEL.REQUEST_CHANGES"));

        assertFalse(actor.permissions().contains("LABEL.SUBMIT_REVIEW"));
    }

    @Test
    void doesNotMapUnknownExternalIdentity() {
        assertTrue(identityRepository
                .findActiveActorByExternalSubject(
                        "DEV_EXTERNAL",
                        "does-not-exist"
                )
                .isEmpty());
    }
}