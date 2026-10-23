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
package io.fireflysoftware.domain.common.contracts.core.documents.v1;

import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuanceReceipt;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.GovernedDocumentPeerPort;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Uncompensated phase coordinator; the native G3A journal is its only durable recovery cursor. */
public class GovernedContractDocumentCoordinator {
  private final GovernedDocumentAccessGuard access;
  private final GovernedDocumentPeerPort peers;
  private final ContractDocumentEnrichmentCollector collector;

  public GovernedContractDocumentCoordinator(
      GovernedDocumentAccessGuard access,
      GovernedDocumentPeerPort peers,
      ContractDocumentEnrichmentCollector collector) {
    this.access = Objects.requireNonNull(access);
    this.peers = Objects.requireNonNull(peers);
    this.collector = Objects.requireNonNull(collector);
  }

  public Mono<ContractDocumentOperation> issue(
      UUID tenant, UUID contract, ContractDocumentPrepareRequest intent) {
    return boundary(
        () -> {
          if (intent == null) throw failure(400, "GOVERNED_DOCUMENT_INTENT_INVALID");
          var captured = SealedContractDocumentRequest.copyIntent(intent);
          UUID action = inputUuid(captured.getActionId());
          return authorized(
                  tenant,
                  contract,
                  action,
                  "issue",
                  context(captured),
                  () -> peers.prepare(tenant, contract, captured))
              .map(result -> checked(tenant, contract, action, captured, result))
              .flatMap(result -> advance(tenant, contract, action, "issue", result, 0));
        });
  }

  public Mono<ContractDocumentOperation> resume(UUID tenant, UUID contract, UUID action) {
    return boundary(
        () ->
            authorized(
                    tenant,
                    contract,
                    action,
                    "resume",
                    Map.of(),
                    () -> peers.get(tenant, contract, action))
                .map(result -> checked(tenant, contract, action, null, result))
                .flatMap(result -> advance(tenant, contract, action, "resume", result, 0)));
  }

  public Mono<ContractDocumentOperation> read(UUID tenant, UUID contract, UUID action) {
    return boundary(
        () ->
            authorized(
                    tenant,
                    contract,
                    action,
                    "read",
                    Map.of(),
                    () -> peers.get(tenant, contract, action))
                .map(result -> checked(tenant, contract, action, null, result))
                .flatMap(
                    result ->
                        authorized(
                            tenant,
                            contract,
                            action,
                            "read",
                            context(result),
                            () -> Mono.just(result))));
  }

  public Mono<io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent> content(
      UUID tenant, UUID contract, UUID action) {
    return boundary(
        () ->
            authorized(
                    tenant,
                    contract,
                    action,
                    "content.read",
                    Map.of(),
                    () -> peers.get(tenant, contract, action))
                .map(result -> checked(tenant, contract, action, null, result))
                .flatMap(
                    result -> {
                      if (result.getPhase() != ContractDocumentOperation.PhaseEnum.ATTACHED)
                        return Mono.error(failure(409, "GOVERNED_DOCUMENT_NOT_ATTACHED"));
                      return authorized(
                              tenant,
                              contract,
                              action,
                              "content.read",
                              context(result),
                              () ->
                                  peers.content(
                                      tenant,
                                      contract,
                                      action,
                                      uuid(result.getIssued().getIssuanceId())))
                          .map(bytes -> verifiedContent(result, bytes))
                          .flatMap(
                              bytes ->
                                  authorized(
                                      tenant,
                                      contract,
                                      action,
                                      "content.read",
                                      context(result),
                                      () -> Mono.just(bytes)));
                    }));
  }

  private static io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent verifiedContent(
      ContractDocumentOperation operation,
      io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent content) {
    var evidence = operation.getIssued();
    if (content == null
        || !Objects.equals(uuid(evidence.getIssuanceId()), content.getIssuanceId())
        || !Objects.equals(uuid(evidence.getDocumentVersionId()), content.getDocumentVersionId())
        || !Objects.equals(evidence.getMediaType(), content.getMediaType())
        || !Objects.equals(evidence.getByteLength(), content.getByteLength())
        || !Objects.equals(evidence.getContentDigest(), content.getContentDigest())
        || content.getContentUtf8() == null
        || content.getContentUtf8().isBlank()
        || content.getContentUtf8().length() > 2097152
        || content
            .getContentUtf8()
            .codePoints()
            .anyMatch(c -> c >= 0xd800 && c <= 0xdfff || c == 0xfffd)) throw invalid();
    byte[] bytes = content.getContentUtf8().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length > 2097152
        || !Objects.equals((long) bytes.length, content.getByteLength())
        || !SealedContractDocumentRequest.digest(content.getContentUtf8())
            .equals(content.getContentDigest())) throw invalid();
    return content;
  }

  private Mono<ContractDocumentOperation> advance(
      UUID tenant,
      UUID contract,
      UUID action,
      String permission,
      ContractDocumentOperation current,
      int steps) {
    if (steps > 4) return Mono.error(invalid());
    var context = context(current);
    return switch (current.getPhase()) {
      case ATTACHED ->
          authorized(tenant, contract, action, permission, context, () -> Mono.just(current));
      case RESERVED ->
          authorized(
                  tenant,
                  contract,
                  action,
                  "collect-facts",
                  context,
                  () -> peers.facts(tenant, contract, action))
              .map(captured -> capture(current, captured))
              .flatMap(
                  captured ->
                      authorized(
                          tenant,
                          contract,
                          action,
                          "collect-facts",
                          context,
                          () -> collector.collect(tenant, contract, action, captured)))
              .flatMap(
                  enrichment ->
                      authorized(
                          tenant,
                          contract,
                          action,
                          permission,
                          context,
                          () -> peers.seal(tenant, contract, action, enrichment)))
              .map(
                  updated ->
                      advanced(
                          tenant,
                          contract,
                          action,
                          current,
                          updated,
                          ContractDocumentOperation.PhaseEnum.READY))
              .flatMap(
                  updated -> advance(tenant, contract, action, permission, updated, steps + 1));
      case READY ->
          authorized(
                  tenant,
                  contract,
                  action,
                  "collect-facts",
                  context,
                  () -> peers.facts(tenant, contract, action))
              .map(captured -> SealedContractDocumentRequest.verify(current, captured))
              .flatMap(
                  retained ->
                      authorized(
                          tenant,
                          contract,
                          action,
                          permission,
                          context,
                          () -> peers.issue(tenant, contract, action, retained)))
              .map(receipt -> candidate(tenant, contract, action, receipt))
              .flatMap(
                  issuance -> {
                    var recordContext = new LinkedHashMap<String, Object>(context);
                    recordContext.put("issuanceId", issuance.toString());
                    return authorized(
                        tenant,
                        contract,
                        action,
                        permission,
                        recordContext,
                        () -> peers.record(tenant, contract, action, issuance));
                  })
              .map(
                  updated ->
                      advanced(
                          tenant,
                          contract,
                          action,
                          current,
                          updated,
                          ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT))
              .flatMap(
                  updated -> advance(tenant, contract, action, permission, updated, steps + 1));
      case ISSUED_PENDING_ATTACHMENT ->
          authorized(
                  tenant,
                  contract,
                  action,
                  permission,
                  context,
                  () -> peers.attach(tenant, contract, action))
              .map(
                  updated ->
                      advanced(
                          tenant,
                          contract,
                          action,
                          current,
                          updated,
                          ContractDocumentOperation.PhaseEnum.ATTACHED))
              .flatMap(
                  updated -> advance(tenant, contract, action, permission, updated, steps + 1));
    };
  }

  private <T> Mono<T> authorized(
      UUID tenant,
      UUID contract,
      UUID action,
      String operation,
      Map<String, Object> context,
      Supplier<Mono<T>> work) {
    return access
        .authorize(tenant, contract, action, operation, context)
        .then(Mono.defer(work))
        .switchIfEmpty(Mono.error(invalid()));
  }

  private static ContractDocumentFactPreparation capture(
      ContractDocumentOperation expected, ContractDocumentFactPreparation captured) {
    if (captured == null
        || captured.getOperation() == null
        || captured.getCoreSnapshotCanonicalJson() == null) throw invalid();
    var actual = captured.getOperation();
    checked(
        UUID.fromString(expected.getTenantId()),
        UUID.fromString(expected.getContractId()),
        UUID.fromString(expected.getActionId()),
        expected.getIntent(),
        actual);
    if (!expected.getOperationId().equals(actual.getOperationId())) throw invalid();
    if (actual.getPhase() != expected.getPhase()
        || !Objects.equals(actual.getSourceRevision(), expected.getSourceRevision())
        || !Objects.equals(actual.getCoreSnapshotDigest(), expected.getCoreSnapshotDigest()))
      throw failure(409, "GOVERNED_DOCUMENT_PHASE_CHANGED");
    return captured;
  }

  private static ContractDocumentOperation advanced(
      UUID tenant,
      UUID contract,
      UUID action,
      ContractDocumentOperation old,
      ContractDocumentOperation next,
      ContractDocumentOperation.PhaseEnum minimum) {
    checked(tenant, contract, action, old.getIntent(), next);
    if (!old.getOperationId().equals(next.getOperationId())
        || !Objects.equals(old.getSourceRevision(), next.getSourceRevision())
        || !Objects.equals(old.getCoreSnapshotDigest(), next.getCoreSnapshotDigest())
        || next.getPhase().ordinal() < minimum.ordinal()) throw invalid();
    if (old.getSealedRequestDigest() != null
        && old.getPhase() != ContractDocumentOperation.PhaseEnum.RESERVED
        && (!Objects.equals(old.getSealedRequestDigest(), next.getSealedRequestDigest())
            || !Objects.equals(old.getFactsDigest(), next.getFactsDigest()))) throw invalid();
    if (old.getIssued() != null && !old.getIssued().equals(next.getIssued())) throw invalid();
    return next;
  }

  private static ContractDocumentOperation checked(
      UUID tenant,
      UUID contract,
      UUID action,
      ContractDocumentPrepareRequest intent,
      ContractDocumentOperation value) {
    if (value == null
        || tenant == null
        || contract == null
        || action == null
        || !tenant.toString().equals(value.getTenantId())
        || !contract.toString().equals(value.getContractId())
        || !action.toString().equals(value.getActionId())
        || value.getIntent() == null
        || !action.toString().equals(value.getIntent().getActionId())
        || value.getPhase() == null
        || intent != null && !intent.equals(value.getIntent())
        || value.getSourceRevision() == null
        || value.getSourceRevision() < 0) throw invalid();
    uuid(value.getOperationId());
    digest(value.getCoreSnapshotDigest());
    if (value.getPhase() != ContractDocumentOperation.PhaseEnum.RESERVED) {
      digest(value.getSealedRequestDigest());
      digest(value.getFactsDigest());
    }
    if (value.getPhase() == ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT
        || value.getPhase() == ContractDocumentOperation.PhaseEnum.ATTACHED) {
      var evidence = value.getIssued();
      if (evidence == null
          || evidence.getVersionNumber() == null
          || evidence.getVersionNumber() < 1
          || evidence.getByteLength() == null
          || evidence.getByteLength() < 1
          || !"text/html".equals(evidence.getMediaType())
          || !Objects.equals(value.getFactsDigest(), evidence.getFactsDigest())) throw invalid();
      uuid(evidence.getIssuanceId());
      uuid(evidence.getDocumentId());
      uuid(evidence.getDocumentVersionId());
      digest(evidence.getContentDigest());
      digest(evidence.getReceiptDigest());
    }
    if (value.getPhase() == ContractDocumentOperation.PhaseEnum.ATTACHED)
      uuid(value.getAttachmentId());
    return value;
  }

  private static UUID candidate(
      UUID tenant, UUID contract, UUID action, DocumentIssuanceReceipt receipt) {
    if (receipt == null
        || receipt.getIssuanceId() == null
        || !tenant.equals(receipt.getTenantId())
        || !contract.equals(receipt.getResourceId())
        || !action.equals(receipt.getActionId())
        || !"CONTRACT".equals(receipt.getResourceType())) throw invalid();
    return receipt.getIssuanceId();
  }

  private static Map<String, Object> context(ContractDocumentOperation operation) {
    var values = new LinkedHashMap<String, Object>(context(operation.getIntent()));
    put(values, "documentSourceRevision", operation.getSourceRevision());
    put(values, "coreSnapshotDigest", operation.getCoreSnapshotDigest());
    put(values, "sealedRequestDigest", operation.getSealedRequestDigest());
    put(values, "factsDigest", operation.getFactsDigest());
    if (operation.getIssued() != null) {
      put(values, "issuanceId", operation.getIssued().getIssuanceId());
      put(values, "documentId", operation.getIssued().getDocumentId());
      put(values, "documentVersionId", operation.getIssued().getDocumentVersionId());
    }
    return values;
  }

  static Map<String, Object> context(ContractDocumentPrepareRequest intent) {
    var values = new LinkedHashMap<String, Object>();
    put(values, "expectedContractRevision", intent.getExpectedContractRevision());
    put(values, "contractDocumentTypeId", intent.getContractDocumentTypeId());
    values.put("factsContractCode", "CONTRACT_DOCUMENT_V1");
    values.put("factsContractVersion", "1");
    var s = intent.getSelection();
    if (s != null) {
      put(values, "documentPurpose", s.getDocumentPurpose());
      put(
          values,
          "productScope",
          s.getProductScope() == null ? null : s.getProductScope().getValue());
      put(values, "productId", s.getProductId());
      put(values, "channelCode", s.getChannelCode());
      put(values, "jurisdiction", s.getJurisdiction());
      put(values, "localeCode", s.getLocaleCode());
      put(values, "businessAtEpochMillis", s.getBusinessAtEpochMillis());
    }
    var p = intent.getExpected();
    if (p != null) {
      put(values, "bindingId", p.getBindingId());
      put(values, "bindingStateVersion", p.getBindingStateVersion());
      put(values, "revisionId", p.getRevisionId());
      put(values, "sourceDigest", p.getSourceDigest());
    }
    var r = intent.getRetention();
    if (r != null) {
      put(values, "retentionPolicyCode", r.getPolicyCode());
      put(values, "retentionPolicyVersion", r.getPolicyVersion());
      put(values, "retainUntilEpochMillis", r.getRetainUntilEpochMillis());
    }
    put(values, "predecessorOperationId", intent.getPredecessorOperationId());
    return values;
  }

  private static void put(Map<String, Object> context, String key, Object value) {
    if (value != null) context.put(key, value);
  }

  private static UUID inputUuid(String value) {
    try {
      return uuid(value);
    } catch (Exception e) {
      throw failure(400, "GOVERNED_DOCUMENT_INTENT_INVALID");
    }
  }

  private static UUID uuid(String value) {
    try {
      var id = UUID.fromString(value);
      if (!id.toString().equals(value)) throw invalid();
      return id;
    } catch (Exception e) {
      throw invalid();
    }
  }

  private static void digest(String value) {
    if (value == null || !value.matches("[a-f0-9]{64}")) throw invalid();
  }

  private static <T> Mono<T> boundary(Supplier<Mono<T>> work) {
    return Mono.defer(work)
        .timeout(Duration.ofMinutes(3))
        .onErrorMap(
            error -> {
              if (error instanceof ResponseStatusException response
                  && response.getReason() != null
                  && Set.of(
                          "GOVERNED_DOCUMENT_ACCESS_DENIED",
                          "GOVERNED_DOCUMENT_INTENT_INVALID",
                          "GOVERNED_DOCUMENT_NOT_ATTACHED",
                          "GOVERNED_DOCUMENT_PHASE_CHANGED",
                          "GOVERNED_DOCUMENT_PEER_INVALID",
                          "GOVERNED_DOCUMENT_PEER_REJECTED",
                          "GOVERNED_DOCUMENT_PEER_UNAVAILABLE",
                          "GOVERNED_DOCUMENT_DELEGATION_REQUIRED",
                          "GOVERNED_DOCUMENT_PEER_NOT_CONFIGURED",
                          "GOVERNED_DOCUMENT_FACTS_UNSUPPORTED")
                      .contains(response.getReason())
                  && response.getStatusCode().value() >= 400
                  && response.getStatusCode().value() <= 599) return response;
              if (error instanceof WebClientResponseException response)
                return failure(response.getStatusCode().value(), "GOVERNED_DOCUMENT_PEER_REJECTED");
              if (error instanceof ResponseStatusException response)
                return failure(response.getStatusCode().value(), "GOVERNED_DOCUMENT_PEER_REJECTED");
              return failure(502, "GOVERNED_DOCUMENT_PEER_INVALID");
            });
  }

  private static ResponseStatusException invalid() {
    return failure(502, "GOVERNED_DOCUMENT_PEER_INVALID");
  }

  private static ResponseStatusException failure(int code, String reason) {
    return new ResponseStatusException(HttpStatusCode.valueOf(code), reason);
  }
}
