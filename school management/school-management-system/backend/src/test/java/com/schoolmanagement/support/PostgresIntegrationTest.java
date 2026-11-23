package com.schoolmanagement.support;

import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need a real PostgreSQL. Uses a Testcontainers instance shared by all subclasses.
 * When Docker is unavailable, set IT_DB_URL (plus IT_DB_USERNAME and IT_DB_PASSWORD) to point at a
 * <strong>throwaway</strong> PostgreSQL database: tests truncate data and Flyway migrates it. Without either,
 * the tests are skipped. H2 is deliberately never used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PostgresIntegrationTest.DatabaseAvailableCondition.class)
public abstract class PostgresIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("IT_DB_URL");
    private static PostgreSQLContainer<?> container;

    static boolean databaseAvailable() {
        return EXTERNAL_URL != null || DockerClientFactory.instance().isDockerAvailable();
    }

    /** Registered through the base class, so it also applies to every subclass (unlike {@code @EnabledIf}). */
    public static class DatabaseAvailableCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return databaseAvailable()
                    ? ConditionEvaluationResult.enabled("A PostgreSQL database is available")
                    : ConditionEvaluationResult.disabled("Neither Docker nor IT_DB_URL is available");
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (!databaseAvailable()) {
            return;
        }
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", ""));
            return;
        }
        PostgreSQLContainer<?> postgres = sharedContainer();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static synchronized PostgreSQLContainer<?> sharedContainer() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        return container;
    }
}
