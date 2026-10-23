/*
 * Copyright 2026 Firefly Software Foundation
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

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import io.fireflysoftware.domain.common.contracts.core.documents.LegacyDocumentMigration;
import java.util.UUID;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Inert legacy boundary: governed recovery uses the producer journal and never deletes evidence.
 */
@Service
public class GenerateDocumentSaga {
  public GenerateDocumentSaga(DocumentControllerApi documents, ContractDocumentsApi contracts) {}

  public Mono<UUID> generateDocument(
      GenerateContractDocumentCommand command, ExecutionContext context) {
    return Mono.error(LegacyDocumentMigration.required());
  }

  public Mono<UUID> attachToContract(
      GenerateContractDocumentCommand command, ExecutionContext context) {
    return Mono.error(LegacyDocumentMigration.required());
  }

  public Mono<Void> deleteDocument(UUID documentId) {
    return Mono.error(LegacyDocumentMigration.required());
  }

  public Mono<Void> detachFromContract(UUID contractDocumentId, ExecutionContext context) {
    return Mono.error(LegacyDocumentMigration.required());
  }
}
