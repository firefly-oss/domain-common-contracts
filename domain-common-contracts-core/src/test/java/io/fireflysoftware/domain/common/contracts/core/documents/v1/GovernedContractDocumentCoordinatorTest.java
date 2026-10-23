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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.commons.ecm.sdk.model.*;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.GovernedDocumentPeerPort;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GovernedContractDocumentCoordinatorTest {
  final UUID tenant = UUID.randomUUID(),
      contract = UUID.randomUUID(),
      action = UUID.randomUUID(),
      operation = UUID.randomUUID(),
      issuance = UUID.randomUUID();
  final UUID document = UUID.randomUUID(),
      version = UUID.randomUUID(),
      attachment = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");
  final AtomicBoolean permitted = new AtomicBoolean(true);
  final List<String> calls = new ArrayList<>();
  final String content = "<html><body>Synthetic García</body></html>";
  final String facts =
      "{\"name\":\"Synthetic"
          + " García\",\"number\":9007199254740993,\"amount\":123.000000000000000001,\"opaque\":1e-128}";
  final ContractDocumentPrepareRequest intent =
      new ContractDocumentPrepareRequest().actionId(action.toString()).expectedContractRevision(7L);
  final DocumentIssueRequest request =
      new DocumentIssueRequest()
          .actionId(action.toString())
          .factsJson(facts)
          .definition(
              new DocumentIssueDefinition()
                  .factsContract(
                      new DocumentFactsContract().code("CONTRACT_DOCUMENT_V1").version("1")));
  final String sealed;
  ContractDocumentOperation.PhaseEnum phase = ContractDocumentOperation.PhaseEnum.RESERVED;
  boolean loseIssueResponse, loseRecordResponse, loseAttachResponse, revokeAfterFacts, returnEmpty;
  int collected, issuedCalls;
  boolean corruptContent, unsupportedCollection, unsafePeerReason;
  boolean mutateIntentDuringPolicy;
  Long preparedRevision;
  ContractDocumentPrepareRequest retainedIntent;
  final GovernedContractDocumentCoordinator coordinator;

  GovernedContractDocumentCoordinatorTest() throws Exception {
    sealed = new ObjectMapper().writeValueAsString(request);
    retainedIntent =
        new ObjectMapper()
            .readValue(
                new ObjectMapper().writeValueAsString(intent),
                ContractDocumentPrepareRequest.class);
    var beans = new StaticListableBeanFactory();
    var principal =
        SecurityPrincipal.builder()
            .subject("synthetic-owner")
            .issuer("synthetic-issuer")
            .tenantId(tenant.toString())
            .claims(Map.of("exp", now.plusSeconds(300)))
            .build();
    beans.addBean("context", (SecurityContextPort) () -> Mono.just(principal));
    beans.addBean(
        "policy",
        (PolicyDecisionPort)
            (p, a, r, c) -> {
              if (mutateIntentDuringPolicy) intent.setExpectedContractRevision(9L);
              return Mono.just(
                  permitted.get() ? Decision.permit() : Decision.deny("synthetic-revocation"));
            });
    var access =
        new GovernedDocumentAccessGuard(
            beans.getBeanProvider(SecurityContextPort.class),
            beans.getBeanProvider(PolicyDecisionPort.class),
            Clock.fixed(now, ZoneOffset.UTC),
            true);
    coordinator =
        new GovernedContractDocumentCoordinator(
            access,
            new Peers(),
            (t, c, a, captured) -> {
              collected++;
              calls.add("collect");
              if (unsupportedCollection)
                return Mono.error(
                    new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "GOVERNED_DOCUMENT_FACTS_UNSUPPORTED"));
              return Mono.just(new ContractDocumentEnrichment());
            });
  }

  ContractDocumentOperation current() {
    return new ContractDocumentOperation()
        .operationId(operation.toString())
        .tenantId(tenant.toString())
        .contractId(contract.toString())
        .actionId(action.toString())
        .intent(retainedIntent)
        .phase(phase)
        .sourceRevision(7L)
        .coreSnapshotDigest("c".repeat(64))
        .factsDigest(SealedContractDocumentRequest.digest(facts))
        .sealedRequestDigest(SealedContractDocumentRequest.digest(sealed))
        .issued(
            phase == ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT
                    || phase == ContractDocumentOperation.PhaseEnum.ATTACHED
                ? new ContractDocumentIssuedEvidence()
                    .issuanceId(issuance.toString())
                    .documentId(document.toString())
                    .documentVersionId(version.toString())
                    .versionNumber(1)
                    .mediaType("text/html")
                    .byteLength(
                        (long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .contentDigest(SealedContractDocumentRequest.digest(content))
                    .factsDigest(SealedContractDocumentRequest.digest(facts))
                    .receiptDigest("b".repeat(64))
                : null)
        .attachmentId(
            phase == ContractDocumentOperation.PhaseEnum.ATTACHED ? attachment.toString() : null);
  }

  class Peers implements GovernedDocumentPeerPort {
    public Mono<DocumentIssuedContent> content(UUID t, UUID c, UUID a, UUID i) {
      calls.add("content");
      return Mono.just(
          new DocumentIssuedContent()
              .issuanceId(issuance)
              .documentVersionId(version)
              .mediaType("text/html")
              .byteLength((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
              .contentDigest(SealedContractDocumentRequest.digest(content))
              .contentUtf8(corruptContent ? content + "altered" : content));
    }

    public Mono<ContractDocumentOperation> prepare(
        UUID t, UUID c, ContractDocumentPrepareRequest i) {
      calls.add("prepare");
      preparedRevision = i.getExpectedContractRevision();
      retainedIntent = i;
      return returnEmpty ? Mono.empty() : Mono.just(current());
    }

    public Mono<ContractDocumentOperation> get(UUID t, UUID c, UUID a) {
      calls.add("get");
      if (unsafePeerReason)
        return Mono.error(
            new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_private synthetic sensitive detail"));
      return Mono.just(current());
    }

    public Mono<ContractDocumentFactPreparation> facts(UUID t, UUID c, UUID a) {
      calls.add("facts");
      if (revokeAfterFacts) permitted.set(false);
      return Mono.just(
          new ContractDocumentFactPreparation()
              .operation(current())
              .coreSnapshotCanonicalJson("{}")
              .sealedFactsJson(facts)
              .sealedIssueRequestJson(sealed));
    }

    public Mono<ContractDocumentOperation> seal(
        UUID t, UUID c, UUID a, ContractDocumentEnrichment e) {
      calls.add("seal");
      phase = ContractDocumentOperation.PhaseEnum.READY;
      return Mono.just(current());
    }

    public Mono<DocumentIssuanceReceipt> issue(UUID t, UUID c, UUID a, DocumentIssueRequest r) {
      calls.add("issue");
      issuedCalls++;
      assertThat(r.getFactsJson()).isEqualTo(facts);
      assertThat(r.getActionId()).isEqualTo(action.toString());
      if (loseIssueResponse) {
        loseIssueResponse = false;
        return Mono.error(
            new ResponseStatusException(HttpStatus.BAD_GATEWAY, "synthetic response lost"));
      }
      return Mono.just(
          new DocumentIssuanceReceipt()
              .issuanceId(issuance)
              .tenantId(tenant)
              .resourceType("CONTRACT")
              .resourceId(contract)
              .actionId(action));
    }

    public Mono<ContractDocumentOperation> record(UUID t, UUID c, UUID a, UUID candidate) {
      calls.add("record");
      assertThat(candidate).isEqualTo(issuance);
      phase = ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT;
      if (loseRecordResponse) {
        loseRecordResponse = false;
        return Mono.error(
            new ResponseStatusException(HttpStatus.BAD_GATEWAY, "synthetic record response lost"));
      }
      return Mono.just(current());
    }

    public Mono<ContractDocumentOperation> attach(UUID t, UUID c, UUID a) {
      calls.add("attach");
      phase = ContractDocumentOperation.PhaseEnum.ATTACHED;
      if (loseAttachResponse) {
        loseAttachResponse = false;
        return Mono.error(
            new ResponseStatusException(HttpStatus.BAD_GATEWAY, "synthetic attach response lost"));
      }
      return Mono.just(current());
    }
  }

  @Test
  void nativePhasesCollectSealIssueRecordAndAttachWithoutSecondJournal() {
    assertThat(coordinator.issue(tenant, contract, intent).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(calls)
        .containsExactly(
            "prepare", "facts", "collect", "seal", "facts", "issue", "record", "attach");
    assertThat(collected).isEqualTo(1);
  }

  @Test
  void readyResumeNeverRecollectsMutableSources() {
    phase = ContractDocumentOperation.PhaseEnum.READY;
    assertThat(coordinator.resume(tenant, contract, action).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(collected).isZero();
    assertThat(calls).containsExactly("get", "facts", "issue", "record", "attach");
  }

  @Test
  void lostIssueResponseReplaysExactRetainedActionWithoutDeleteCompensation() {
    phase = ContractDocumentOperation.PhaseEnum.READY;
    loseIssueResponse = true;
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(phase).isEqualTo(ContractDocumentOperation.PhaseEnum.READY);
    assertThat(coordinator.resume(tenant, contract, action).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(issuedCalls).isEqualTo(2);
    assertThat(collected).isZero();
  }

  @Test
  void lostRecordResponseResumesAttachmentWithoutReissueOrCollection() {
    phase = ContractDocumentOperation.PhaseEnum.READY;
    loseRecordResponse = true;
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(phase).isEqualTo(ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT);
    calls.clear();
    assertThat(coordinator.resume(tenant, contract, action).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(calls).containsExactly("get", "attach");
    assertThat(issuedCalls).isEqualTo(1);
  }

  @Test
  void lostAttachResponseReturnsCurrentRetainedAssociationOnNextResume() {
    phase = ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT;
    loseAttachResponse = true;
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    calls.clear();
    assertThat(coordinator.resume(tenant, contract, action).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(calls).containsExactly("get");
    assertThat(issuedCalls).isZero();
    assertThat(collected).isZero();
  }

  @Test
  void currentDenialPreventsAnyPeerEffect() {
    permitted.set(false);
    assertThatThrownBy(() -> coordinator.issue(tenant, contract, intent).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    assertThat(calls).isEmpty();
  }

  @Test
  void policyWithdrawnAfterPrivateReadPreventsIssuance() {
    phase = ContractDocumentOperation.PhaseEnum.READY;
    revokeAfterFacts = true;
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    assertThat(calls).containsExactly("get", "facts");
    assertThat(issuedCalls).isZero();
  }

  @Test
  void callerMutationDuringPolicyCannotChangeTheApprovedPreparedIntent() {
    mutateIntentDuringPolicy = true;
    assertThat(coordinator.issue(tenant, contract, intent).block().getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ATTACHED);
    assertThat(preparedRevision).isEqualTo(7L);
    assertThat(intent.getExpectedContractRevision()).isEqualTo(9L);
  }

  @Test
  void unsupportedProfileRetainsReservedJournalWithoutSealingIssuingOrAttaching() {
    unsupportedCollection = true;
    assertThatThrownBy(() -> coordinator.issue(tenant, contract, intent).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
    assertThat(phase).isEqualTo(ContractDocumentOperation.PhaseEnum.RESERVED);
    assertThat(calls).containsExactly("prepare", "facts", "collect");
    calls.clear();
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
    assertThat(calls).containsExactly("get", "facts", "collect");
    calls.clear();
    permitted.set(false);
    assertThatThrownBy(() -> coordinator.resume(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    assertThat(calls).isEmpty();
    assertThat(issuedCalls).isZero();
  }

  @Test
  void arbitraryPeerReasonCannotBecomePublicThroughASharedPrefix() {
    unsafePeerReason = true;
    assertThatThrownBy(() -> coordinator.read(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_PEER_REJECTED"));
  }

  @Test
  void emptyPrepareResponseIsExplicitDependencyError() {
    returnEmpty = true;
    assertThatThrownBy(() -> coordinator.issue(tenant, contract, intent).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
    assertThat(calls).containsExactly("prepare");
  }

  @Test
  void attachedMetadataReadRechecksCurrentAuthorityWithoutPrivateFacts() {
    phase = ContractDocumentOperation.PhaseEnum.ATTACHED;
    assertThat(coordinator.read(tenant, contract, action).block().getAttachmentId())
        .isEqualTo(attachment.toString());
    assertThat(calls).containsExactly("get");
    permitted.set(false);
    calls.clear();
    assertThatThrownBy(() -> coordinator.read(tenant, contract, action).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(calls).isEmpty();
  }

  @Test
  void actualContentReadRequiresRetainedAttachedEvidenceAndExactBytes() {
    phase = ContractDocumentOperation.PhaseEnum.ATTACHED;
    assertThat(coordinator.content(tenant, contract, action).block().getContentUtf8())
        .isEqualTo(content);
    assertThat(calls).containsExactly("get", "content");
    corruptContent = true;
    assertThatThrownBy(() -> coordinator.content(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
  }

  @Test
  void pendingContentDoesNotBecomeEmptySuccess() {
    phase = ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT;
    assertThatThrownBy(() -> coordinator.content(tenant, contract, action).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    assertThat(calls).containsExactly("get");
  }
}
