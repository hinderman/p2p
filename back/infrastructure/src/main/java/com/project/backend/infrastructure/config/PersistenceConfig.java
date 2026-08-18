package com.project.backend.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Persistence configuration.
 *
 * <p>Required because the main class lives in {@code com.project.backend.api}; Spring
 * Boot would otherwise scan for entities and repositories from that package. This
 * configuration identifies their actual location in this module.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.project.backend.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.project.backend.infrastructure.persistence.entity")
public class PersistenceConfig {
}
