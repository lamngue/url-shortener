package com.example.shortener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * A real Redis for integration tests, same major version as docker-compose.yml. Testcontainers
 * has no Redis module, so this is a generic container that Boot recognizes by the name "redis".
 * Kept separate from Postgres so a test can leave Redis out (see the fallback test).
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisContainerConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8").withExposedPorts(6379);
    }
}
