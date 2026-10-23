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

import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.ContractDocumentSourcePort;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class CapturedContractEnrichmentCollectorTest {
  final UUID tenant = UUID.randomUUID(),
      contract = UUID.randomUUID(),
      action = UUID.randomUUID(),
      party = UUID.randomUUID(),
      role = UUID.randomUUID(),
      product = UUID.randomUUID(),
      version = UUID.randomUUID(),
      type = UUID.randomUUID();
  final List<String> reads = new java.util.concurrent.CopyOnWriteArrayList<>();
  final ContractDocumentSourcePort sources =
      new ContractDocumentSourcePort() {
        public Mono<ContractDocumentPartyObservation> party(UUID t, UUID c, UUID a, UUID p) {
          reads.add("party/" + p);
          return Mono.just(
              new ContractDocumentPartyObservation().partyId(p.toString()).tenantId(t.toString()));
        }

        public Mono<ContractDocumentProductObservation> product(
            UUID t, UUID c, UUID a, UUID p, UUID v) {
          reads.add("product/" + p + "/" + v);
          return Mono.just(
              new ContractDocumentProductObservation()
                  .productId(p.toString())
                  .productVersionId(v.toString())
                  .tenantId(t.toString()));
        }

        public Mono<ContractDocumentRoleObservation> role(UUID t, UUID c, UUID a, UUID r) {
          reads.add("role/" + r);
          return Mono.just(new ContractDocumentRoleObservation().roleId(r.toString()));
        }

        public Mono<ContractDocumentTypeObservation> documentType(UUID t, UUID c, UUID a, UUID id) {
          reads.add("type/" + id);
          return Mono.just(new ContractDocumentTypeObservation().documentTypeId(id.toString()));
        }
      };

  ContractDocumentFactPreparation capture(String extraParties, String extraLines) {
    String snapshot =
        "{\"contract\":{\"contract_id\":\""
            + contract
            + "\",\"tenant_id\":\""
            + tenant
            + "\",\"document_source_revision\":7,\"product_id\":\""
            + product
            + "\"},\"businessAtEpochMillis\":1735689600000,\"parties\":[{\"party_id\":\""
            + party
            + "\",\"role_in_contract_id\":\""
            + role
            + "\",\"is_active\":true,\"date_joined\":\"2025-01-01T00:00:00\"}"
            + extraParties
            + "],\"lines\":[{\"product_id\":\""
            + product
            + "\",\"is_primary\":true,\"effective_from\":\"2025-01-01T00:00:00\"}"
            + extraLines
            + "],\"validationRules\":[],\"terms\":[]}";
    var intent =
        new ContractDocumentPrepareRequest()
            .actionId(action.toString())
            .expectedContractRevision(7L)
            .contractDocumentTypeId(type.toString())
            .selection(new ContractDocumentSelection().businessAtEpochMillis(1735689600000L))
            .productVersionReferences(
                new ArrayList<>(
                    List.of(
                        new ContractProductVersionReference()
                            .productId(product.toString())
                            .productVersionId(version.toString()))));
    return new ContractDocumentFactPreparation()
        .coreSnapshotCanonicalJson(snapshot)
        .operation(
            new ContractDocumentOperation()
                .tenantId(tenant.toString())
                .contractId(contract.toString())
                .actionId(action.toString())
                .phase(ContractDocumentOperation.PhaseEnum.RESERVED)
                .sourceRevision(7L)
                .coreSnapshotDigest(SealedContractDocumentRequest.digest(snapshot))
                .intent(intent));
  }

  void rejects(ContractDocumentFactPreparation captured) {
    assertThatThrownBy(
            () ->
                new CapturedContractEnrichmentCollector(sources)
                    .collect(tenant, contract, action, captured)
                    .block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
    assertThat(reads).isEmpty();
  }

  @Test
  void onlyExactEffectiveIdentitiesAreReadAndDeduplicated() {
    String inactive = ",{\"party_id\":\"" + UUID.randomUUID() + "\",\"is_active\":false}";
    String future =
        ",{\"product_id\":\""
            + UUID.randomUUID()
            + "\",\"effective_from\":\"2025-01-01T00:00:00.001\"}";
    var result =
        new CapturedContractEnrichmentCollector(sources)
            .collect(tenant, contract, action, capture(inactive, future))
            .block();
    assertThat(result.getParties())
        .extracting(ContractDocumentPartyObservation::getPartyId)
        .containsExactly(party.toString());
    assertThat(result.getProducts())
        .extracting(ContractDocumentProductObservation::getProductVersionId)
        .containsExactly(version.toString());
    assertThat(reads)
        .containsExactlyInAnyOrder(
            "party/" + party, "role/" + role, "product/" + product + "/" + version, "type/" + type);
  }

  @Test
  void totalConcurrentForeignReadsStayBoundedAcrossEverySourceFamily() {
    String extra = "";
    for (int i = 0; i < 3; i++)
      extra +=
          ",{\"party_id\":\""
              + UUID.randomUUID()
              + "\",\"role_in_contract_id\":\""
              + role
              + "\",\"is_active\":true}";
    var active = new AtomicInteger();
    var peak = new AtomicInteger();
    var delayed =
        new ContractDocumentSourcePort() {
          <T> Mono<T> waitFor(Mono<T> value) {
            return Mono.defer(
                () -> {
                  peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                  return Mono.delay(Duration.ofMillis(40))
                      .then(value)
                      .doOnSuccess(ignored -> active.decrementAndGet());
                });
          }

          public Mono<ContractDocumentPartyObservation> party(UUID t, UUID c, UUID a, UUID p) {
            return waitFor(sources.party(t, c, a, p));
          }

          public Mono<ContractDocumentProductObservation> product(
              UUID t, UUID c, UUID a, UUID p, UUID v) {
            return waitFor(sources.product(t, c, a, p, v));
          }

          public Mono<ContractDocumentRoleObservation> role(UUID t, UUID c, UUID a, UUID r) {
            return waitFor(sources.role(t, c, a, r));
          }

          public Mono<ContractDocumentTypeObservation> documentType(
              UUID t, UUID c, UUID a, UUID id) {
            return waitFor(sources.documentType(t, c, a, id));
          }
        };
    assertThat(
            new CapturedContractEnrichmentCollector(delayed)
                .collect(tenant, contract, action, capture(extra, ""))
                .block()
                .getParties())
        .hasSize(4);
    assertThat(peak.get()).isLessThanOrEqualTo(4);
    assertThat(active.get()).isZero();
  }

  @Test
  void nativeSupportedPreEpochBusinessDatesRemainValid() {
    var captured = capture("", "");
    String exact =
        captured
            .getCoreSnapshotCanonicalJson()
            .replace("1735689600000", "-315619200000")
            .replace("2025-01-01T00:00:00", "1960-01-01T00:00:00");
    captured.setCoreSnapshotCanonicalJson(exact);
    captured.getOperation().setCoreSnapshotDigest(SealedContractDocumentRequest.digest(exact));
    captured.getOperation().getIntent().getSelection().setBusinessAtEpochMillis(-315619200000L);
    assertThat(
            new CapturedContractEnrichmentCollector(sources)
                .collect(tenant, contract, action, captured)
                .block()
                .getParties())
        .hasSize(1);
  }

  @Test
  void snapshotDigestAndScopeMustMatchBeforeAnyForeignRead() {
    var captured = capture("", "");
    captured.setCoreSnapshotCanonicalJson(captured.getCoreSnapshotCanonicalJson() + " ");
    rejects(captured);
    captured = capture("", "");
    captured.getOperation().setTenantId(UUID.randomUUID().toString());
    rejects(captured);
  }

  @Test
  void productVersionsMustEqualTheCompleteCapturedEffectiveProductSet() {
    var captured = capture("", "");
    captured.getOperation().getIntent().setProductVersionReferences(List.of());
    rejects(captured);
    captured = capture("", "");
    captured
        .getOperation()
        .getIntent()
        .addProductVersionReferencesItem(
            new ContractProductVersionReference()
                .productId(UUID.randomUUID().toString())
                .productVersionId(UUID.randomUUID().toString()));
    rejects(captured);
  }

  @Test
  void duplicateRelationshipsAndInvalidIntervalsNeverReachSources() {
    var duplicate =
        ",{\"party_id\":\""
            + party
            + "\",\"role_in_contract_id\":\""
            + role
            + "\",\"is_active\":true}";
    rejects(capture(duplicate, ""));
    var captured = capture("", "");
    String invalid =
        captured
            .getCoreSnapshotCanonicalJson()
            .replace(
                "\"effective_from\":\"2025-01-01T00:00:00\"",
                "\"effective_from\":\"2025-01-01T00:00:00\",\"effective_to\":\"2024-12-31T23:59:59\"");
    captured.setCoreSnapshotCanonicalJson(invalid);
    captured.getOperation().setCoreSnapshotDigest(SealedContractDocumentRequest.digest(invalid));
    rejects(captured);
  }

  @Test
  void unsupportedValidationRulesUseExplicitProfileErrorBeforeForeignReads() {
    var captured = capture("", "");
    String invalid =
        captured
            .getCoreSnapshotCanonicalJson()
            .replace("\"validationRules\":[]", "\"validationRules\":[{}]");
    captured.setCoreSnapshotCanonicalJson(invalid);
    captured.getOperation().setCoreSnapshotDigest(SealedContractDocumentRequest.digest(invalid));
    assertThatThrownBy(
            () ->
                new CapturedContractEnrichmentCollector(sources)
                    .collect(tenant, contract, action, captured)
                    .block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(422);
              assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_FACTS_UNSUPPORTED");
            });
    assertThat(reads).isEmpty();
  }

  @Test
  void over100RowsFailBeforeForeignReads() {
    rejects(capture(",{\"is_active\":false}".repeat(100), ""));
  }
}
