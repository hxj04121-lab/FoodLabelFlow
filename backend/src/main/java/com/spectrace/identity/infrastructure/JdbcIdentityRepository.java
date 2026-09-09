package com.spectrace.identity.infrastructure;

import com.spectrace.identity.application.port.IdentityRepository;
import com.spectrace.identity.domain.AuthenticatedActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthenticatedActor> findActiveActorByExternalSubject(
            String authProvider,
            String externalSubject
    ) {
        List<UserRow> users = jdbcTemplate.query(
                """
                SELECT user_id, username, display_name
                FROM user_account
                WHERE auth_provider = ?
                  AND external_auth_subject = ?
                  AND is_active = 'Y'
                """,
                (rs, rowNum) -> new UserRow(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name")
                ),
                authProvider,
                externalSubject
        );

        if (users.isEmpty()) {
            return Optional.empty();
        }

        UserRow user = users.getFirst();

        Set<String> roles = new HashSet<>(jdbcTemplate.queryForList(
                """
                SELECT r.role_code
                FROM user_role ur
                JOIN `role` r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                """,
                String.class,
                user.userId()
        ));

        Set<String> permissions = new HashSet<>(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT p.permission_code
                FROM user_role ur
                JOIN role_permission rp ON rp.role_id = ur.role_id
                JOIN permission p ON p.permission_id = rp.permission_id
                WHERE ur.user_id = ?
                """,
                String.class,
                user.userId()
        ));

        return Optional.of(new AuthenticatedActor(
                user.userId(),
                user.username(),
                user.displayName(),
                roles,
                permissions
        ));
    }

    private record UserRow(
            String userId,
            String username,
            String displayName
    ) {
    }
}