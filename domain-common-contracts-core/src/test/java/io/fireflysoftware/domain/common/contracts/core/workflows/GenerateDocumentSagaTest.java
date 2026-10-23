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

package io.fireflysoftware.domain.common.contracts.core.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import java.util.UUID;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class GenerateDocumentSagaTest {
  @Test
  void legacyMetadataWorkflowIsNotRegisteredForExecution() {
    assertThat(GenerateDocumentSaga.class.getAnnotation(Saga.class)).isNull();
  }

  @Test
  void directMetadataGenerationRequiresMigrationWithoutPeerEffects() {
    var documents = mock(DocumentControllerApi.class);
    var contracts = mock(ContractDocumentsApi.class);
    var saga = new GenerateDocumentSaga(documents, contracts);
    StepVerifier.create(
            saga.generateDocument(
                new GenerateContractDocumentCommand(UUID.randomUUID(), UUID.randomUUID()),
                ExecutionContext.forSaga("synthetic", "legacy")))
        .expectError(ResponseStatusException.class)
        .verify();
    verifyNoInteractions(documents, contracts);
  }

  @Test
  void directOrdinaryAttachmentRequiresMigrationWithoutPeerEffects() {
    var documents = mock(DocumentControllerApi.class);
    var contracts = mock(ContractDocumentsApi.class);
    var saga = new GenerateDocumentSaga(documents, contracts);
    StepVerifier.create(
            saga.attachToContract(
                new GenerateContractDocumentCommand(UUID.randomUUID(), UUID.randomUUID()),
                ExecutionContext.forSaga("synthetic", "legacy")))
        .expectError(ResponseStatusException.class)
        .verify();
    verifyNoInteractions(documents, contracts);
  }
}
