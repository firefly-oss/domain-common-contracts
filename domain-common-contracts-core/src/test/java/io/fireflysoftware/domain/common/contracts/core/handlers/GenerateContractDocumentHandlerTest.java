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

package io.fireflysoftware.domain.common.contracts.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import java.util.UUID;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class GenerateContractDocumentHandlerTest {
  @Test
  void templateOnlyCommandRequiresGovernedIntentBeforeSagaEffects() {
    SagaEngine engine = mock(SagaEngine.class);
    var handler = new GenerateContractDocumentHandler(engine);
    StepVerifier.create(
            handler.doHandle(
                new GenerateContractDocumentCommand(UUID.randomUUID(), UUID.randomUUID())))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> {
                          assertThat(failure.getStatusCode().value()).isEqualTo(409);
                          assertThat(failure.getReason())
                              .isEqualTo("GOVERNED_DOCUMENT_INTENT_REQUIRED");
                        }))
        .verify();
    verifyNoInteractions(engine);
  }
}
