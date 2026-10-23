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

import io.fireflysoftware.commons.ecm.sdk.api.GovernedDocumentIssuanceApi;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuanceReceipt;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssueRequest;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentOperationsApi;
import io.fireflysoftware.core.contract.sdk.model.*;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Each operation gets an isolated generated client and a fresh, expiring delegated credential. */
@Component
public class GovernedDocumentSdkAdapter implements GovernedDocumentPeerPort {
  private final GovernedPeerEndpoint contracts;
  private final GovernedPeerEndpoint documents;
  private final GovernedDocumentCredentialPort credentials;
  private final Clock clock;

  @Autowired
  public GovernedDocumentSdkAdapter(
      @Value("${firefly.contracts.documents.peers.contracts.base-url:}") String contracts,
      @Value("${firefly.contracts.documents.peers.documents.base-url:}") String documents,
      @Value("${firefly.contracts.documents.peers.contracts.allow-loopback-http:false}")
          boolean contractLoopback,
      @Value("${firefly.contracts.documents.peers.documents.allow-loopback-http:false}")
          boolean documentLoopback,
      GovernedDocumentCredentialPort credentials) {
    this(
        new GovernedPeerEndpoint(contracts, contractLoopback),
        new GovernedPeerEndpoint(documents, documentLoopback),
        credentials,
        Clock.systemUTC());
  }

  GovernedDocumentSdkAdapter(
      GovernedPeerEndpoint contracts,
      GovernedPeerEndpoint documents,
      GovernedDocumentCredentialPort credentials,
      Clock clock) {
    this.contracts = Objects.requireNonNull(contracts);
    this.documents = Objects.requireNonNull(documents);
    this.credentials = Objects.requireNonNull(credentials);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<ContractDocumentOperation> prepare(
      UUID tenant, UUID contract, ContractDocumentPrepareRequest intent) {
    return Mono.defer(
        () -> {
          if (intent == null) return Mono.error(fail(400, "GOVERNED_DOCUMENT_INTENT_INVALID"));
          UUID action;
          try {
            action = UUID.fromString(intent.getActionId());
            if (!action.toString().equals(intent.getActionId()))
              throw new IllegalArgumentException();
          } catch (Exception error) {
            return Mono.error(fail(400, "GOVERNED_DOCUMENT_INTENT_INVALID"));
          }
          return contract(
              tenant,
              contract,
              action,
              api -> api.prepareContractDocument(tenant, contract, intent));
        });
  }

  @Override
  public Mono<ContractDocumentOperation> get(UUID tenant, UUID contract, UUID action) {
    return contract(
        tenant,
        contract,
        action,
        api -> api.getContractDocumentOperation(tenant, contract, action, null));
  }

  @Override
  public Mono<ContractDocumentFactPreparation> facts(UUID tenant, UUID contract, UUID action) {
    return contract(
        tenant,
        contract,
        action,
        api -> api.getContractDocumentFacts(tenant, contract, action, null));
  }

  @Override
  public Mono<ContractDocumentOperation> seal(
      UUID tenant, UUID contract, UUID action, ContractDocumentEnrichment enrichment) {
    return contract(
        tenant,
        contract,
        action,
        api -> api.sealContractDocument(tenant, contract, action, enrichment));
  }

  @Override
  public Mono<ContractDocumentOperation> record(
      UUID tenant, UUID contract, UUID action, UUID issuance) {
    return contract(
        tenant,
        contract,
        action,
        api ->
            api.recordContractDocumentIssuance(
                tenant,
                contract,
                action,
                new ContractDocumentRecordIssuanceRequest().issuanceId(issuance.toString())));
  }

  @Override
  public Mono<ContractDocumentOperation> attach(UUID tenant, UUID contract, UUID action) {
    return contract(
        tenant,
        contract,
        action,
        api -> api.attachContractDocumentIssuance(tenant, contract, action));
  }

  @Override
  public Mono<DocumentIssuanceReceipt> issue(
      UUID tenant, UUID contract, UUID action, DocumentIssueRequest retained) {
    return Mono.defer(
            () -> {
              if (retained == null || !action.toString().equals(retained.getActionId()))
                return Mono.error(fail(502, "GOVERNED_DOCUMENT_PEER_INVALID"));
              return credential(
                      GovernedDocumentCredentialPort.Peer.DOCUMENTS, tenant, contract, action)
                  .flatMap(
                      token -> {
                        var client = documentClient();
                        client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
                        return new GovernedDocumentIssuanceApi(client)
                            .issueGovernedDocument(
                                tenant.toString(), "CONTRACT", contract.toString(), retained, null);
                      });
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  @Override
  public Mono<DocumentIssuedContent> content(
      UUID tenant, UUID contract, UUID action, UUID issuance) {
    return credential(GovernedDocumentCredentialPort.Peer.DOCUMENTS, tenant, contract, action)
        .flatMap(
            token -> {
              if (issuance == null) return Mono.error(fail(502, "GOVERNED_DOCUMENT_PEER_INVALID"));
              var client = documentClient();
              client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
              return new GovernedDocumentIssuanceApi(client)
                  .getGovernedDocumentContent(
                      tenant.toString(),
                      "CONTRACT",
                      contract.toString(),
                      issuance.toString(),
                      null);
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private <T> Mono<T> contract(
      UUID tenant,
      UUID contract,
      UUID action,
      Function<ContractDocumentOperationsApi, Mono<T>> work) {
    return credential(GovernedDocumentCredentialPort.Peer.CONTRACTS, tenant, contract, action)
        .flatMap(
            token -> {
              var client = contractClient();
              client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
              return work.apply(new ContractDocumentOperationsApi(client));
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private io.fireflysoftware.core.contract.sdk.invoker.ApiClient contractClient() {
    var format = io.fireflysoftware.core.contract.sdk.invoker.ApiClient.createDefaultDateFormat();
    var mapper =
        GovernedPeerCodec.strict(
            io.fireflysoftware.core.contract.sdk.invoker.ApiClient.createDefaultMapper(format));
    var web =
        io.fireflysoftware.core.contract.sdk.invoker.ApiClient.buildWebClientBuilder(mapper)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(GovernedPeerCodec.MAX_BODY_BYTES))
            .build();
    return new io.fireflysoftware.core.contract.sdk.invoker.ApiClient(web, mapper, format)
        .setBasePath(contracts.required());
  }

  private io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient documentClient() {
    var format = io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient.createDefaultDateFormat();
    var mapper =
        GovernedPeerCodec.strict(
            io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient.createDefaultMapper(format));
    var web =
        io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient.buildWebClientBuilder(mapper)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(GovernedPeerCodec.MAX_BODY_BYTES))
            .build();
    return new io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient(web, mapper, format)
        .setBasePath(documents.required());
  }

  private Mono<GovernedDocumentCredentialPort.Credential> credential(
      GovernedDocumentCredentialPort.Peer peer, UUID tenant, UUID contract, UUID action) {
    return Mono.defer(
        () -> {
          if (tenant == null || contract == null || action == null)
            return Mono.error(fail(502, "GOVERNED_DOCUMENT_PEER_INVALID"));
          return credentials
              .current(peer, tenant, contract, action)
              .filter(
                  c ->
                      c != null
                          && c.bearer() != null
                          && c.bearer().length() <= 16384
                          && c.bearer().matches("[A-Za-z0-9._~+/=-]+")
                          && c.expiresAt() != null
                          && c.expiresAt().isAfter(clock.instant()))
              .switchIfEmpty(Mono.error(fail(503, "GOVERNED_DOCUMENT_DELEGATION_REQUIRED")));
        });
  }

  static <T> Mono<T> bounded(Mono<T> work) {
    return work.timeout(Duration.ofSeconds(4))
        .switchIfEmpty(Mono.error(fail(502, "GOVERNED_DOCUMENT_PEER_INVALID")))
        .onErrorMap(
            error -> {
              if (error instanceof WebClientResponseException response)
                return fail(response.getStatusCode().value(), "GOVERNED_DOCUMENT_PEER_REJECTED");
              if (error instanceof ResponseStatusException response)
                return fail(response.getStatusCode().value(), "GOVERNED_DOCUMENT_PEER_REJECTED");
              return fail(502, "GOVERNED_DOCUMENT_PEER_UNAVAILABLE");
            });
  }

  private static ResponseStatusException fail(int status, String reason) {
    return new ResponseStatusException(HttpStatusCode.valueOf(status), reason);
  }
}
