package com.spectrace.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared MySQL fixture for integration tests.
 *
 * <p>A single MySQL container is started once for the test JVM so cached
 * Spring application contexts never retain a JDBC URL for a container that
 * Testcontainers has already stopped.</p>
 */
public abstract class MySqlIntegrationTestSupport {

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("spectrace")
                    .withUsername("spectrace_test")
                    .withPassword("spectrace_test_password");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername
        );
        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword
        );
        registry.add(
                "spring.flyway.enabled",
                () -> true
        );
    }
}