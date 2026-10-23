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
package io.fireflysoftware.domain.common.contracts.core.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LegacyDocumentMigrationTest {
  @Test
  void templateOnlyKitIssueRejectsBeforeCreatingMetadata() {
    DocumentControllerApi peer = mock(DocumentControllerApi.class);
    when(peer.createDocument(any(), any()))
        .thenReturn(Mono.just(new DocumentDTO(UUID.randomUUID())));
    KitDocumentService service = new KitDocumentService(peer);

    StepVerifier.create(service.issue("contract", UUID.randomUUID(), UUID.randomUUID()))
        .expectErrorSatisfies(LegacyDocumentMigrationTest::requiresGovernedIntent)
        .verify();
    verifyNoInteractions(peer);
  }

  @Test
  void unscopedKitReadRejectsRatherThanReturningEmptyContent() {
    DocumentControllerApi peer = mock(DocumentControllerApi.class);
    UUID id = UUID.randomUUID();
    DocumentDTO stored = new DocumentDTO(id);
    stored.setName("contract");
    stored.setMimeType("text/html");
    when(peer.getDocumentById(any(), any())).thenReturn(Mono.just(stored));
    KitDocumentService service = new KitDocumentService(peer);

    StepVerifier.create(service.read(id))
        .expectErrorSatisfies(LegacyDocumentMigrationTest::requiresGovernedIntent)
        .verify();
    verifyNoInteractions(peer);
  }

  @Test
  void oldDeleteCompensationCannotRemoveIssuedEvidence() {
    DocumentControllerApi peer = mock(DocumentControllerApi.class);
    var contracts = mock(io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi.class);
    when(peer.deleteDocument(any(), any())).thenReturn(Mono.empty());
    var legacy =
        new io.fireflysoftware.domain.common.contracts.core.workflows.GenerateDocumentSaga(
            peer, contracts);
    StepVerifier.create(legacy.deleteDocument(UUID.randomUUID()))
        .expectErrorSatisfies(LegacyDocumentMigrationTest::requiresGovernedIntent)
        .verify();
    verifyNoInteractions(peer, contracts);
  }

  @Test
  void oldDetachCompensationCannotRemoveRetainedContractAssociation() {
    DocumentControllerApi peer = mock(DocumentControllerApi.class);
    var contracts = mock(io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi.class);
    when(contracts.deleteContractDocument(any(), any(), any())).thenReturn(Mono.empty());
    var context =
        org.fireflyframework.orchestration.core.context.ExecutionContext.forSaga(
            "synthetic", "legacy");
    context.putVariable("contractId", UUID.randomUUID());
    var legacy =
        new io.fireflysoftware.domain.common.contracts.core.workflows.GenerateDocumentSaga(
            peer, contracts);
    StepVerifier.create(legacy.detachFromContract(UUID.randomUUID(), context))
        .expectErrorSatisfies(LegacyDocumentMigrationTest::requiresGovernedIntent)
        .verify();
    verifyNoInteractions(peer, contracts);
  }

  private static void requiresGovernedIntent(Throwable error) {
    assertThat(error)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(failure.getReason()).isEqualTo("GOVERNED_DOCUMENT_INTENT_REQUIRED");
            });
  }
}
