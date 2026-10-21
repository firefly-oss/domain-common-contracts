/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.contracts.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application class used only for integration tests in the -core module.
 * Scans the core package so that saga beans and CQRS handlers are discovered and registered
 * with the SagaEngine and CommandBus auto-configured by fireflyframework-starter-domain.
 */
@SpringBootApplication(scanBasePackages = "io.fireflysoftware.domain.common.contracts.core")
public class TestContractsCoreApplication {
}
