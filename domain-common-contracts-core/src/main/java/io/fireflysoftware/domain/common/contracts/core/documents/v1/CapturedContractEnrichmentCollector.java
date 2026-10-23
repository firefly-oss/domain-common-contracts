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

import com.fasterxml.jackson.databind.JsonNode;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.ContractDocumentSourcePort;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Selects a bounded complete identity set before performing any foreign source observation. */
public final class CapturedContractEnrichmentCollector
    implements ContractDocumentEnrichmentCollector {
  private final ContractDocumentSourcePort sources;

  public CapturedContractEnrichmentCollector(ContractDocumentSourcePort sources) {
    this.sources = Objects.requireNonNull(sources);
  }

  @Override
  public Mono<ContractDocumentEnrichment> collect(
      UUID tenant, UUID contract, UUID action, ContractDocumentFactPreparation captured) {
    return Mono.defer(
            () -> {
              var selected = select(tenant, contract, action, captured);
              var work = new ArrayList<Mono<Consumer<ContractDocumentEnrichment>>>();
              for (var id : selected.parties())
                work.add(
                    Mono.defer(() -> sources.party(tenant, contract, action, id))
                        .filter(
                            p ->
                                id.toString().equals(p.getPartyId())
                                    && tenant.toString().equals(p.getTenantId()))
                        .switchIfEmpty(Mono.error(invalid()))
                        .<Consumer<ContractDocumentEnrichment>>map(
                            value -> result -> result.addPartiesItem(value)));
              for (var id : selected.roles())
                work.add(
                    Mono.defer(() -> sources.role(tenant, contract, action, id))
                        .filter(r -> id.toString().equals(r.getRoleId()))
                        .switchIfEmpty(Mono.error(invalid()))
                        .<Consumer<ContractDocumentEnrichment>>map(
                            value -> result -> result.addRolesItem(value)));
              for (var ref : selected.products().entrySet())
                work.add(
                    Mono.defer(
                            () ->
                                sources.product(
                                    tenant, contract, action, ref.getKey(), ref.getValue()))
                        .filter(
                            p ->
                                ref.getKey().toString().equals(p.getProductId())
                                    && ref.getValue().toString().equals(p.getProductVersionId())
                                    && tenant.toString().equals(p.getTenantId()))
                        .switchIfEmpty(Mono.error(invalid()))
                        .<Consumer<ContractDocumentEnrichment>>map(
                            value -> result -> result.addProductsItem(value)));
              work.add(
                  Mono.defer(() -> sources.documentType(tenant, contract, action, selected.type()))
                      .filter(t -> selected.type().toString().equals(t.getDocumentTypeId()))
                      .switchIfEmpty(Mono.error(invalid()))
                      .<Consumer<ContractDocumentEnrichment>>map(
                          value -> result -> result.documentType(value)));
              return Flux.fromIterable(work)
                  .flatMapSequential(value -> value, 4, 1)
                  .collectList()
                  .map(
                      additions -> {
                        var result =
                            new ContractDocumentEnrichment()
                                .parties(new ArrayList<>())
                                .roles(new ArrayList<>())
                                .products(new ArrayList<>());
                        additions.forEach(add -> add.accept(result));
                        return result;
                      });
            })
        .timeout(Duration.ofSeconds(30));
  }

  private record Selected(
      List<UUID> parties, List<UUID> roles, Map<UUID, UUID> products, UUID type) {}

  private static Selected select(
      UUID tenant, UUID contract, UUID action, ContractDocumentFactPreparation captured) {
    try {
      if (tenant == null
          || contract == null
          || action == null
          || captured == null
          || captured.getOperation() == null) throw invalid();
      var op = captured.getOperation();
      var intent = op.getIntent();
      if (!tenant.toString().equals(op.getTenantId())
          || !contract.toString().equals(op.getContractId())
          || !action.toString().equals(op.getActionId())
          || op.getPhase() != ContractDocumentOperation.PhaseEnum.RESERVED
          || intent == null
          || !action.toString().equals(intent.getActionId())
          || op.getSourceRevision() == null
          || !Objects.equals(op.getSourceRevision(), intent.getExpectedContractRevision())
          || !SealedContractDocumentRequest.digest(captured.getCoreSnapshotCanonicalJson())
              .equals(op.getCoreSnapshotDigest())) throw invalid();
      var tree = SealedContractDocumentRequest.snapshot(captured.getCoreSnapshotCanonicalJson());
      var header = tree.path("contract");
      if (!header.isObject()
          || !tenant.toString().equals(text(header, "tenant_id"))
          || !contract.toString().equals(text(header, "contract_id"))
          || integer(header.path("document_source_revision")) != op.getSourceRevision()
          || intent.getSelection() == null
          || intent.getSelection().getBusinessAtEpochMillis() == null) throw invalid();
      long at = integer(tree.path("businessAtEpochMillis"));
      if (at < -2208988800000L
          || at > 253402300799999L
          || at != intent.getSelection().getBusinessAtEpochMillis()) throw invalid();
      var parties = new TreeSet<String>();
      var roles = new TreeSet<String>();
      var relationships = new HashSet<String>();
      for (var row : rows(tree, "parties")) {
        var active = row.path("is_active");
        if (!active.isBoolean()) throw invalid();
        if (!active.booleanValue() || !effective(row, "date_joined", "date_left", at)) continue;
        String party = text(row, "party_id"), role = text(row, "role_in_contract_id");
        uuid(party);
        uuid(role);
        if (!relationships.add(party + "/" + role)) throw invalid();
        parties.add(party);
        roles.add(role);
      }
      if (parties.isEmpty()) throw invalid();
      var products = new TreeSet<String>();
      String primary = null;
      for (var row : rows(tree, "lines")) {
        if (!effective(row, "effective_from", "effective_to", at)) continue;
        String product = text(row, "product_id");
        uuid(product);
        products.add(product);
        var isPrimary = row.path("is_primary");
        if (!isPrimary.isMissingNode() && !isPrimary.isNull() && !isPrimary.isBoolean())
          throw invalid();
        if (isPrimary.isBoolean() && isPrimary.booleanValue()) {
          if (primary != null) throw invalid();
          primary = product;
        }
      }
      var headerProduct = header.path("product_id");
      String expectedPrimary =
          headerProduct.isNull() || headerProduct.isMissingNode()
              ? null
              : text(header, "product_id");
      if (products.isEmpty()
          ? expectedPrimary != null
          : primary == null || !primary.equals(expectedPrimary)) throw invalid();
      if (intent.getProductVersionReferences() == null
          || intent.getProductVersionReferences().size() > 100) throw invalid();
      var refs = new TreeMap<String, UUID>();
      for (var ref : intent.getProductVersionReferences()) {
        if (ref == null
            || refs.put(uuid(ref.getProductId()).toString(), uuid(ref.getProductVersionId()))
                != null) throw invalid();
      }
      if (!refs.keySet().equals(products)) throw invalid();
      if (!rows(tree, "validationRules").isEmpty())
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "GOVERNED_DOCUMENT_FACTS_UNSUPPORTED");
      var versions = new LinkedHashMap<UUID, UUID>();
      refs.forEach((key, value) -> versions.put(uuid(key), value));
      return new Selected(
          parties.stream().map(CapturedContractEnrichmentCollector::uuid).toList(),
          roles.stream().map(CapturedContractEnrichmentCollector::uuid).toList(),
          Collections.unmodifiableMap(versions),
          uuid(intent.getContractDocumentTypeId()));
    } catch (ResponseStatusException error) {
      throw error;
    } catch (Exception error) {
      throw invalid();
    }
  }

  private static List<JsonNode> rows(JsonNode parent, String name) {
    var array = parent.path(name);
    if (!array.isArray() || array.size() > 100) throw invalid();
    var result = new ArrayList<JsonNode>();
    array.forEach(
        row -> {
          if (!row.isObject()) throw invalid();
          result.add(row);
        });
    return result;
  }

  private static boolean effective(JsonNode row, String from, String to, long at) {
    Long start = date(row.path(from)), end = date(row.path(to));
    if (start != null && end != null && end <= start) throw invalid();
    return (start == null || start <= at) && (end == null || at < end);
  }

  private static Long date(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) return null;
    if (!node.isTextual() || node.textValue().length() > 40) throw invalid();
    long value = LocalDateTime.parse(node.textValue()).toInstant(ZoneOffset.UTC).toEpochMilli();
    if (value < -2208988800000L || value > 253402300799999L) throw invalid();
    return value;
  }

  private static long integer(JsonNode value) {
    if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
    return value.longValue();
  }

  private static String text(JsonNode parent, String name) {
    var value = parent.path(name);
    if (!value.isTextual() || value.textValue().length() > 100) throw invalid();
    return value.textValue();
  }

  private static UUID uuid(String value) {
    var id = UUID.fromString(value);
    if (!id.toString().equals(value)) throw invalid();
    return id;
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_PEER_INVALID");
  }
}
