package com.ahmedb.internship;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for the tests, when Docker is available.
 *
 * <p>Only active under the {@code testcontainers} profile. This is the configuration that runs the
 * Flyway migration against a real database with {@code ddl-auto=validate}, which is what catches
 * drift between {@code V1__initial_schema.sql} and the entity mappings -- something the H2 profile,
 * where Hibernate generates the schema, cannot see.
 *
 * <p>Run it with: {@code ./gradlew test -Ptestcontainers}
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("testcontainers")
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
