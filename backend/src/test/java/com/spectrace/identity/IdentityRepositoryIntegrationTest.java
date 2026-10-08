package com.spectrace.identity;

import com.spectrace.identity.application.port.IdentityRepository;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityRepositoryIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @Transactional
    void listsOnlyActiveHoldersOfAPermissionInUserIdOrder() {
        assertEquals(List.of("user_label_officer"),
                identityRepository.findActiveUserIdsWithPermission("LABEL.CREATE"));
        assertEquals(List.of(), identityRepository.findActiveUserIdsWithPermission("NO.SUCH_PERMISSION"));

        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, assigned_at) "
                + "VALUES ('user_admin', 'role_label_officer', '2026-10-01 00:00:00')");
        assertEquals(List.of("user_admin", "user_label_officer"),
                identityRepository.findActiveUserIdsWithPermission("LABEL.CREATE"));

        jdbcTemplate.update("UPDATE user_account SET is_active = 'N' WHERE user_id = 'user_admin'");
        assertEquals(List.of("user_label_officer"),
                identityRepository.findActiveUserIdsWithPermission("LABEL.CREATE"));
    }
}
