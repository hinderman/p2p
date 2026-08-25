package com.project.backend.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Owns a disposable PostgreSQL database for integration tests.
 *
 * <p>Docker/Testcontainers is the default and cannot reuse its container. An
 * externally managed database is accepted only for local verification when its
 * name starts with {@code project_it_} and the caller explicitly confirms that
 * the complete test schema may be dropped. This prevents a test from ever
 * connecting to the development or production database by accident.
 */
final class EphemeralPostgreSql implements AutoCloseable {
    static final String EXTERNAL_DATABASE_CONFIRMATION = "DROP_AFTER_TESTS";

    private final PostgreSQLContainer container;
    private final DriverManagerDataSource dataSource;
    private final boolean externallyManaged;

    private EphemeralPostgreSql(
            PostgreSQLContainer container,
            DriverManagerDataSource dataSource,
            boolean externallyManaged) {
        this.container = container;
        this.dataSource = dataSource;
        this.externallyManaged = externallyManaged;
    }

    static EphemeralPostgreSql start() {
        String externalUrl = environment("TEST_DATABASE_URL");
        EphemeralPostgreSql database = externalUrl == null ? startContainer() : useExternalDatabase(externalUrl);
        try {
            database.provision();
            return database;
        } catch (RuntimeException | Error failure) {
            database.close();
            throw failure;
        }
    }

    DataSource dataSource() {
        return dataSource;
    }

    private static EphemeralPostgreSql startContainer() {
        String databaseName = "project_it_" + UUID.randomUUID().toString().replace("-", "");
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName(databaseName)
                .withUsername("project_it")
                .withPassword(UUID.randomUUID().toString())
                .withReuse(false);
        container.start();
        return new EphemeralPostgreSql(container,
                dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword()), false);
    }

    private static EphemeralPostgreSql useExternalDatabase(String url) {
        if (!EXTERNAL_DATABASE_CONFIRMATION.equals(environment("TEST_DATABASE_EPHEMERAL"))) {
            throw new IllegalStateException("An external integration database requires TEST_DATABASE_EPHEMERAL="
                    + EXTERNAL_DATABASE_CONFIRMATION);
        }
        String username = requiredEnvironment("TEST_DATABASE_USERNAME");
        String password = requiredEnvironment("TEST_DATABASE_PASSWORD");
        DriverManagerDataSource dataSource = dataSource(url, username, password);
        String databaseName = new JdbcTemplate(dataSource).queryForObject("SELECT current_database()", String.class);
        if (databaseName == null || !databaseName.startsWith("project_it_")) {
            throw new IllegalStateException(
                    "Refusing to use an external database whose name does not start with project_it_: " + databaseName);
        }
        return new EphemeralPostgreSql(null, dataSource, true);
    }

    private void provision() {
        executeScript("database/00_loans_schema.sql");
        executeScript("database/01_reference_data.sql");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load()
                .migrate();
    }

    private void executeScript(String classpathLocation) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(classpathLocation));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    @Override
    public void close() {
        if (externallyManaged) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("DROP SCHEMA IF EXISTS loans CASCADE");
            jdbc.execute("DROP TABLE IF EXISTS public.flyway_schema_history");
        }
        if (container != null) {
            container.stop();
        }
    }

    private static DriverManagerDataSource dataSource(String url, String username, String password) {
        return new DriverManagerDataSource(url, username, password);
    }

    private static String requiredEnvironment(String name) {
        String value = environment(name);
        if (value == null) {
            throw new IllegalStateException(name + " is required when TEST_DATABASE_URL is set");
        }
        return value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
