package com.firefly.domain.common.contracts.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application class used only for integration tests in the -core module.
 * Scans the core package so that saga beans and CQRS handlers are discovered and registered
 * with the SagaEngine and CommandBus auto-configured by fireflyframework-starter-domain.
 */
@SpringBootApplication(scanBasePackages = "com.firefly.domain.common.contracts.core")
public class TestContractsCoreApplication {
}
