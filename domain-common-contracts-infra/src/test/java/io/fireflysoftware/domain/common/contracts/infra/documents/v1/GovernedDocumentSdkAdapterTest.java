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
package io.fireflysoftware.domain.common.contracts.infra.documents.v1;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssueRequest;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.infra.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;

class GovernedDocumentSdkAdapterTest {
  final UUID tenant = UUID.randomUUID(),
      contract = UUID.randomUUID(),
      action = UUID.randomUUID(),
      issuance = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");
  final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
  final List<GovernedDocumentCredentialPort.Peer> peers = new CopyOnWriteArrayList<>();
  final ObjectMapper json = new ObjectMapper();
  HttpServer server;
  volatile String overrideResponse;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(
              Map.of(
                  "path",
                  exchange.getRequestURI().getPath(),
                  "method",
                  exchange.getRequestMethod(),
                  "authorization",
                  Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"), ""),
                  "body",
                  body));
          byte[] response =
              (overrideResponse != null
                      ? overrideResponse
                      : exchange.getRequestURI().getPath().endsWith("/facts")
                          ? "{\"operation\":{\"phase\":\"READY\"}}"
                          : exchange.getRequestURI().getPath().contains("document-issuances")
                              ? "{\"issuanceId\":\"" + issuance + "\"}"
                              : "{\"operationId\":\""
                                  + UUID.randomUUID()
                                  + "\",\"phase\":\"READY\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(response);
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  GovernedDocumentSdkAdapter adapter(GovernedDocumentCredentialPort credentials) {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    var contracts = new ContractMgmtProperties();
    contracts.setBasePath(base);
    var documents = new DocumentMgmtProperties();
    documents.setBasePath(base);
    return new GovernedDocumentSdkAdapter(
        new GovernedPeerEndpoint(base, true),
        new GovernedPeerEndpoint(base, true),
        credentials,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  GovernedDocumentCredentialPort credential() {
    return (peer, t, c, a) -> {
      assertThat(t).isEqualTo(tenant);
      assertThat(c).isEqualTo(contract);
      assertThat(a).isEqualTo(action);
      peers.add(peer);
      return Mono.just(
          new GovernedDocumentCredentialPort.Credential("synthetic-" + peer, now.plusSeconds(60)));
    };
  }

  @Test
  void existingNativeJournalMethodsUseActualGeneratedSdkWithExactTuple() throws Exception {
    var adapter = adapter(credential());
    var intent = new ContractDocumentPrepareRequest().actionId(action.toString());
    assertThat(adapter.prepare(tenant, contract, intent).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.READY);
    adapter.get(tenant, contract, action).block();
    adapter.facts(tenant, contract, action).block();
    adapter.seal(tenant, contract, action, new ContractDocumentEnrichment()).block();
    adapter.record(tenant, contract, action, issuance).block();
    adapter.attach(tenant, contract, action).block();
    assertThat(requests).hasSize(6);
    assertThat(peers).containsOnly(GovernedDocumentCredentialPort.Peer.CONTRACTS);
    assertThat(requests)
        .allSatisfy(
            r -> {
              assertThat(r.get("path"))
                  .startsWith(
                      "/api/v1/tenants/"
                          + tenant
                          + "/contracts/"
                          + contract
                          + "/document-operations");
              assertThat(r.get("authorization")).isEqualTo("Bearer synthetic-CONTRACTS");
            });
    assertThat(requests.get(4).get("path")).endsWith("/" + action + "/issuance");
    assertThat(json.readTree(requests.get(4).get("body")).path("issuanceId").asText())
        .isEqualTo(issuance.toString());
  }

  @Test
  void genuineDocumentSdkTransportsExactFactsString() throws Exception {
    var adapter = adapter(credential());
    String facts =
        "{\"name\":\"García"
            + " \uD83D\uDE00\",\"large\":9007199254740993,\"amount\":123.000000000000000001,\"opaque\":1e-128}";
    var result =
        adapter
            .issue(
                tenant,
                contract,
                action,
                new DocumentIssueRequest().actionId(action.toString()).factsJson(facts))
            .block();
    assertThat(result.getIssuanceId()).isEqualTo(issuance);
    assertThat(requests).hasSize(1);
    assertThat(peers).containsExactly(GovernedDocumentCredentialPort.Peer.DOCUMENTS);
    assertThat(requests.get(0).get("authorization")).isEqualTo("Bearer synthetic-DOCUMENTS");
    assertThat(json.readTree(requests.get(0).get("body")).path("factsJson").asText())
        .isEqualTo(facts);
  }

  @Test
  void unavailableCredentialsFailBeforeAnyHttpRequest() {
    var adapter = adapter((p, t, c, a) -> Mono.empty());
    assertThatThrownBy(() -> adapter.get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(requests).isEmpty();
  }

  @Test
  void expiredCredentialsFailBeforeAnyHttpRequest() {
    var adapter =
        adapter(
            (p, t, c, a) ->
                Mono.just(new GovernedDocumentCredentialPort.Credential("synthetic-expired", now)));
    assertThatThrownBy(() -> adapter.get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(requests).isEmpty();
  }

  @Test
  void concurrentOperationsNeverShareMutableSdkAuthorizationHeaders() {
    AtomicInteger counter = new AtomicInteger();
    var adapter =
        adapter(
            (p, t, c, a) ->
                Mono.just(
                    new GovernedDocumentCredentialPort.Credential(
                        "synthetic-call-" + counter.incrementAndGet(), now.plusSeconds(60))));
    Flux.range(0, 16).flatMap(i -> adapter.get(tenant, contract, action), 8).collectList().block();
    assertThat(requests).hasSize(16);
    assertThat(requests).extracting(r -> r.get("authorization")).doesNotHaveDuplicates();
  }

  @Test
  void fractionalNativeRevisionIsRejectedBeforeItCanBecomeALong() {
    overrideResponse =
        "{\"operationId\":\""
            + UUID.randomUUID()
            + "\",\"phase\":\"READY\",\"sourceRevision\":7.5}";
    assertThatThrownBy(() -> adapter(credential()).get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void numericIdentifierCannotBeCoercedIntoText() {
    overrideResponse = "{\"operationId\":123,\"phase\":\"READY\"}";
    assertThatThrownBy(() -> adapter(credential()).get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void duplicateNativeRevisionIsRejectedRatherThanSelectingOneValue() {
    overrideResponse =
        "{\"operationId\":\""
            + UUID.randomUUID()
            + "\",\"phase\":\"READY\",\"sourceRevision\":7,\"sourceRevision\":9}";
    assertThatThrownBy(() -> adapter(credential()).get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unknownCurrentProfileResponseFieldsFailExplicitly() {
    overrideResponse =
        "{\"operationId\":\"" + UUID.randomUUID() + "\",\"phase\":\"READY\",\"unexpected\":true}";
    assertThatThrownBy(() -> adapter(credential()).get(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void legitimateNumericRevisionAndBusinessTimestampKeepTheirTypes() {
    overrideResponse =
        "{\"operationId\":\""
            + UUID.randomUUID()
            + "\",\"phase\":\"READY\",\"sourceRevision\":9007199254740993,\"createdAtEpochMillis\":1792627200123}";
    var result = adapter(credential()).get(tenant, contract, action).block();
    assertThat(result.getSourceRevision()).isEqualTo(9007199254740993L);
    assertThat(result.getCreatedAtEpochMillis()).isEqualTo(1792627200123L);
  }

  @Test
  void genuineSdkReadsBoundedContentAboveDefaultCodecBufferWithoutChangingUnicode()
      throws Exception {
    String exact = "<html><body>García " + "x".repeat(300000) + "</body></html>";
    UUID version = UUID.randomUUID();
    overrideResponse =
        json.writeValueAsString(
            Map.of(
                "issuanceId",
                issuance.toString(),
                "documentVersionId",
                version.toString(),
                "mediaType",
                "text/html",
                "byteLength",
                (long) exact.getBytes(StandardCharsets.UTF_8).length,
                "contentDigest",
                "a".repeat(64),
                "contentUtf8",
                exact));
    var result = adapter(credential()).content(tenant, contract, action, issuance).block();
    assertThat(result.getContentUtf8()).isEqualTo(exact);
    assertThat(result.getDocumentVersionId()).isEqualTo(version);
    assertThat(peers).containsExactly(GovernedDocumentCredentialPort.Peer.DOCUMENTS);
    assertThat(requests.get(0).get("path"))
        .isEqualTo(
            "/api/v1/document-issuances/tenants/"
                + tenant
                + "/resources/CONTRACT/"
                + contract
                + "/"
                + issuance
                + "/content");
  }

  @Test
  void legacyEndpointsCannotConfigureGovernedContractOrDocumentCalls() {
    try (var context =
        new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var c = new ContractMgmtProperties();
      c.setBasePath(base);
      var d = new DocumentMgmtProperties();
      d.setBasePath(base);
      context.registerBean(ContractMgmtProperties.class, () -> c);
      context.registerBean(DocumentMgmtProperties.class, () -> d);
      context.registerBean(
          GovernedDocumentCredentialPort.class,
          () ->
              (p, t, id, a) ->
                  Mono.just(
                      new GovernedDocumentCredentialPort.Credential(
                          "synthetic-current", Instant.now().plusSeconds(60))));
      context.register(GovernedDocumentSdkAdapter.class);
      context.refresh();
      var adapter = context.getBean(GovernedDocumentSdkAdapter.class);
      assertThatThrownBy(() -> adapter.get(tenant, contract, action).block())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
      assertThatThrownBy(
              () ->
                  adapter
                      .issue(
                          tenant,
                          contract,
                          action,
                          new DocumentIssueRequest().actionId(action.toString()))
                      .block())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
      assertThat(requests).isEmpty();
    }
  }
}
