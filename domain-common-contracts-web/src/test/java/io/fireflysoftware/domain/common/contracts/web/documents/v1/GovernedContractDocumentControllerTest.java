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
package io.fireflysoftware.domain.common.contracts.web.documents.v1;

import static org.mockito.Mockito.*;

import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentOperation;
import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedContractDocumentCoordinator;
import io.fireflysoftware.domain.common.contracts.web.controllers.document.v1.GovernedContractDocumentController;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GovernedContractDocumentControllerTest {
  final UUID tenant = UUID.randomUUID(),
      contract = UUID.randomUUID(),
      action = UUID.randomUUID(),
      version = UUID.randomUUID();
  final GovernedContractDocumentCoordinator coordinator =
      mock(GovernedContractDocumentCoordinator.class);
  final WebTestClient http =
      WebTestClient.bindToController(new GovernedContractDocumentController(coordinator)).build();

  String base() {
    return "/api/v1/tenants/" + tenant + "/contracts/" + contract + "/governed-documents";
  }

  @Test
  void malformedIntentRejectsBeforeAnyCoordinatorOrPeerEffect() {
    http.post()
        .uri(base())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"expectedContractRevision\":7.5,\"tenantId\":\"" + tenant + "\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(coordinator);
  }

  @Test
  void contentReturnsExactUtf8BytesWithNoStoreAndImmutableVersionDigest() {
    String exact = "<html><body>García 😀</body></html>", digest = "a".repeat(64);
    byte[] bytes = exact.getBytes(StandardCharsets.UTF_8);
    when(coordinator.content(tenant, contract, action))
        .thenReturn(
            Mono.just(
                new DocumentIssuedContent()
                    .documentVersionId(version)
                    .contentUtf8(exact)
                    .contentDigest(digest)
                    .byteLength((long) bytes.length)
                    .mediaType("text/html")));
    http.get()
        .uri(base() + "/" + action + "/content")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType("text/html;charset=UTF-8")
        .expectHeader()
        .valueEquals("Cache-Control", "no-store")
        .expectHeader()
        .valueEquals("ETag", "\"" + digest + "\"")
        .expectHeader()
        .valueEquals("X-Document-Version-Id", version.toString())
        .expectHeader()
        .contentLength(bytes.length)
        .expectBody(byte[].class)
        .isEqualTo(bytes);
  }

  @Test
  void currentContentDenialHasNoSuccessfulContentHeadersOrBytes() {
    when(coordinator.content(tenant, contract, action))
        .thenReturn(
            Mono.error(
                new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "GOVERNED_DOCUMENT_ACCESS_DENIED")));
    http.get()
        .uri(base() + "/" + action + "/content")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist("ETag")
        .expectHeader()
        .doesNotExist("X-Document-Version-Id");
  }

  @Test
  void resumeAndReadUseExplicitRouteScopeAndDisableCachingOfCurrentViews() {
    var value =
        new ContractDocumentOperation()
            .actionId(action.toString())
            .phase(ContractDocumentOperation.PhaseEnum.ATTACHED);
    when(coordinator.resume(tenant, contract, action)).thenReturn(Mono.just(value));
    when(coordinator.read(tenant, contract, action)).thenReturn(Mono.just(value));
    http.post()
        .uri(base() + "/" + action + "/resume")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Cache-Control", "no-store")
        .expectBody()
        .jsonPath("$.actionId")
        .isEqualTo(action.toString());
    http.get()
        .uri(base() + "/" + action)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Cache-Control", "no-store");
  }
}
