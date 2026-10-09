package com.spectrace.support.fixture;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Installs only the real V10 persistence prefix in isolated V2 golden databases. */
public final class ValidationCurrentRunFixture {
    private static final String RESOURCE = "db/migration/V10__persist_current_validation_run.sql";
    private static final String END_MARKER = "-- END VALIDATION PERSISTENCE V10";

    private ValidationCurrentRunFixture() { }

    public static void install(DataSource dataSource) {
        var jdbc = new JdbcTemplate(dataSource);
        var originalHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        if (!jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class)
                .equals(List.of("1", "2"))) {
            throw new IllegalStateException("This fixture requires the unchanged V2 golden baseline");
        }
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            String productionMigration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int marker = productionMigration.indexOf(END_MARKER);
            if (marker < 0 || productionMigration.indexOf(END_MARKER, marker + END_MARKER.length()) >= 0) {
                throw new IllegalStateException("Expected exactly one production persistence boundary");
            }
            // Execute the exact production SQL with Flyway's official MySQL parser.
            // The separate fixture history leaves the V2 Flyway history and seed intact.
            var directory = Files.createTempDirectory("food-validation-persistence-v10-");
            var script = directory.resolve("V10__validation_persistence_fixture.sql");
            Files.writeString(script, productionMigration.substring(0, marker), StandardCharsets.UTF_8);
            var migration = Flyway.configure().dataSource(dataSource)
                    .table("validation_persistence_fixture_history")
                    .baselineOnMigrate(true).baselineVersion("9").target("10")
                    .locations("filesystem:" + directory.toAbsolutePath())
                    .load().migrate();
            if (migration.migrationsExecuted != 1) {
                throw new IllegalStateException("Expected the one explicit production persistence fixture");
            }
            if (!jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")
                    .equals(originalHistory)) {
                throw new IllegalStateException("The V2 golden migration history changed");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
