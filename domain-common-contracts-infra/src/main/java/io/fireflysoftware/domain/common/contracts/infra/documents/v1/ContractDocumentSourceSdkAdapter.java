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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.common.reference.master.data.sdk.api.ContractDocumentTypeApi;
import io.fireflysoftware.common.reference.master.data.sdk.api.ContractRoleApi;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.core.customer.sdk.api.*;
import io.fireflysoftware.core.customer.sdk.model.PartyDTO;
import io.fireflysoftware.core.product.sdk.api.ProductApi;
import io.fireflysoftware.core.product.sdk.api.ProductVersionApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Minimal source projections observed through isolated, current-credential generated SDK calls. */
@Component
public final class ContractDocumentSourceSdkAdapter implements ContractDocumentSourcePort {
  private static final ObjectMapper FINGERPRINT_JSON = new ObjectMapper();
  private final GovernedPeerEndpoint customers, products, masters;
  private final GovernedDocumentCredentialPort credentials;
  private final Clock clock;

  @Autowired
  public ContractDocumentSourceSdkAdapter(
      @Value("${firefly.contracts.documents.peers.customers.base-url:}") String customers,
      @Value("${firefly.contracts.documents.peers.products.base-url:}") String products,
      @Value("${firefly.contracts.documents.peers.masters.base-url:}") String masters,
      @Value("${firefly.contracts.documents.peers.customers.allow-loopback-http:false}")
          boolean customerLoopback,
      @Value("${firefly.contracts.documents.peers.products.allow-loopback-http:false}")
          boolean productLoopback,
      @Value("${firefly.contracts.documents.peers.masters.allow-loopback-http:false}")
          boolean masterLoopback,
      GovernedDocumentCredentialPort credentials) {
    this(
        new GovernedPeerEndpoint(customers, customerLoopback),
        new GovernedPeerEndpoint(products, productLoopback),
        new GovernedPeerEndpoint(masters, masterLoopback),
        credentials,
        Clock.systemUTC());
  }

  ContractDocumentSourceSdkAdapter(
      GovernedPeerEndpoint customers,
      GovernedPeerEndpoint products,
      GovernedPeerEndpoint masters,
      GovernedDocumentCredentialPort credentials,
      Clock clock) {
    this.customers = Objects.requireNonNull(customers);
    this.products = Objects.requireNonNull(products);
    this.masters = Objects.requireNonNull(masters);
    this.credentials = Objects.requireNonNull(credentials);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<ContractDocumentPartyObservation> party(
      UUID tenant, UUID contract, UUID action, UUID party) {
    return customer(
            tenant, contract, action, client -> new PartiesApi(client).getPartyById(party, null))
        .flatMap(
            value -> {
              if (!party.equals(value.getPartyId())
                  || !tenant.equals(value.getTenantId())
                  || value.getPartyKind() == null) return Mono.error(invalid());
              if (value.getPartyKind() == PartyDTO.PartyKindEnum.INDIVIDUAL) {
                return customer(
                        tenant,
                        contract,
                        action,
                        client ->
                            new NaturalPersonsApi(client).getNaturalPersonByPartyId(party, null))
                    .map(
                        profile -> {
                          if (!party.equals(profile.getPartyId())
                              || profile.getNaturalPersonId() == null) throw invalid();
                          if ((profile.getGivenName() == null || profile.getGivenName().isBlank())
                              && (profile.getFamilyName() == null
                                  || profile.getFamilyName().isBlank())) throw invalid();
                          var names = new ArrayList<String>();
                          optionalName(names, profile.getGivenName());
                          optionalName(names, profile.getMiddleName());
                          optionalName(names, profile.getFamilyName());
                          optionalName(names, profile.getSecondFamilyName());
                          String display = name(String.join(" ", names), 1000);
                          return new ContractDocumentPartyObservation()
                              .partyId(party.toString())
                              .tenantId(tenant.toString())
                              .partyType("INDIVIDUAL")
                              .displayName(display)
                              .source(
                                  observation(
                                      "CUSTOMERS",
                                      "PARTY",
                                      party,
                                      tenant,
                                      party,
                                      "INDIVIDUAL",
                                      profile.getNaturalPersonId(),
                                      display,
                                      date(value.getCreatedAt()),
                                      date(value.getUpdatedAt()),
                                      date(profile.getCreatedAt()),
                                      date(profile.getUpdatedAt())));
                        });
              }
              if (value.getPartyKind() == PartyDTO.PartyKindEnum.ORGANIZATION) {
                return customer(
                        tenant,
                        contract,
                        action,
                        client -> new LegalEntitiesApi(client).getLegalEntityByPartyId(party, null))
                    .map(
                        profile -> {
                          if (!party.equals(profile.getPartyId())
                              || profile.getLegalEntityId() == null) throw invalid();
                          String display = name(profile.getLegalName(), 1000);
                          return new ContractDocumentPartyObservation()
                              .partyId(party.toString())
                              .tenantId(tenant.toString())
                              .partyType("ORGANIZATION")
                              .displayName(display)
                              .source(
                                  observation(
                                      "CUSTOMERS",
                                      "PARTY",
                                      party,
                                      tenant,
                                      party,
                                      "ORGANIZATION",
                                      profile.getLegalEntityId(),
                                      display,
                                      date(value.getCreatedAt()),
                                      date(value.getUpdatedAt()),
                                      date(profile.getCreatedAt()),
                                      date(profile.getUpdatedAt())));
                        });
              }
              return Mono.error(invalid());
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  @Override
  public Mono<ContractDocumentProductObservation> product(
      UUID tenant, UUID contract, UUID action, UUID product, UUID version) {
    return productCall(
            tenant,
            contract,
            action,
            client -> new ProductApi(client).getProductById(product, null))
        .flatMap(
            value -> {
              if (!product.equals(value.getProductId()) || !tenant.equals(value.getTenantId()))
                return Mono.error(invalid());
              return productCall(
                      tenant,
                      contract,
                      action,
                      client ->
                          new ProductVersionApi(client)
                              .getProductVersionById(product, version, null))
                  .map(
                      v -> {
                        if (!version.equals(v.getProductVersionId())
                            || !product.equals(v.getProductId())
                            || v.getVersionNumber() == null
                            || v.getVersionNumber() < 1
                            || v.getEffectiveDate() == null) throw invalid();
                        String code = name(value.getProductCode(), 100),
                            name = name(value.getProductName(), 1000),
                            effective = date(v.getEffectiveDate());
                        return new ContractDocumentProductObservation()
                            .productId(product.toString())
                            .tenantId(tenant.toString())
                            .productVersionId(version.toString())
                            .productCode(code)
                            .productName(name)
                            .versionNumber(v.getVersionNumber())
                            .versionEffectiveDate(effective)
                            .source(
                                observation(
                                    "PRODUCTS",
                                    "PRODUCT_VERSION",
                                    version,
                                    tenant,
                                    product,
                                    version,
                                    code,
                                    name,
                                    v.getVersionNumber(),
                                    effective,
                                    date(value.getDateCreated()),
                                    date(value.getDateUpdated()),
                                    date(v.getDateCreated()),
                                    date(v.getDateUpdated())));
                      });
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  @Override
  public Mono<ContractDocumentRoleObservation> role(
      UUID tenant, UUID contract, UUID action, UUID role) {
    return master(
            tenant,
            contract,
            action,
            client -> new ContractRoleApi(client).getContractRole(role, null))
        .map(
            value -> {
              if (!role.equals(value.getRoleId()) || !Boolean.TRUE.equals(value.getIsActive()))
                throw invalid();
              String code = name(value.getRoleCode(), 100), name = name(value.getName(), 255);
              return new ContractDocumentRoleObservation()
                  .roleId(role.toString())
                  .code(code)
                  .name(name)
                  .active(true)
                  .source(
                      observation(
                          "MASTERS",
                          "CONTRACT_ROLE",
                          role,
                          role,
                          code,
                          name,
                          true,
                          date(value.getDateCreated()),
                          date(value.getDateUpdated())));
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  @Override
  public Mono<ContractDocumentTypeObservation> documentType(
      UUID tenant, UUID contract, UUID action, UUID type) {
    return master(
            tenant,
            contract,
            action,
            client -> new ContractDocumentTypeApi(client).getContractDocumentType(type, null))
        .map(
            value -> {
              if (!type.equals(value.getDocumentTypeId())
                  || !Boolean.TRUE.equals(value.getIsActive())) throw invalid();
              String code = name(value.getDocumentCode(), 50), name = name(value.getName(), 100);
              return new ContractDocumentTypeObservation()
                  .documentTypeId(type.toString())
                  .code(code)
                  .name(name)
                  .active(true)
                  .source(
                      observation(
                          "MASTERS",
                          "CONTRACT_DOCUMENT_TYPE",
                          type,
                          type,
                          code,
                          name,
                          true,
                          date(value.getDateCreated()),
                          date(value.getDateUpdated())));
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private <T> Mono<T> customer(
      UUID tenant,
      UUID contract,
      UUID action,
      Function<io.fireflysoftware.core.customer.sdk.invoker.ApiClient, Mono<T>> read) {
    return credential(GovernedDocumentCredentialPort.Peer.CUSTOMERS, tenant, contract, action)
        .flatMap(
            token -> {
              var format =
                  io.fireflysoftware.core.customer.sdk.invoker.ApiClient.createDefaultDateFormat();
              var mapper =
                  GovernedPeerCodec.strict(
                      io.fireflysoftware.core.customer.sdk.invoker.ApiClient.createDefaultMapper(
                          format));
              var web =
                  io.fireflysoftware.core.customer.sdk.invoker.ApiClient.buildWebClientBuilder(
                          mapper)
                      .codecs(
                          c -> c.defaultCodecs().maxInMemorySize(GovernedPeerCodec.MAX_BODY_BYTES))
                      .build();
              var client =
                  new io.fireflysoftware.core.customer.sdk.invoker.ApiClient(web, mapper, format)
                      .setBasePath(customers.required());
              client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
              return read.apply(client);
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private <T> Mono<T> productCall(
      UUID tenant,
      UUID contract,
      UUID action,
      Function<io.fireflysoftware.core.product.sdk.invoker.ApiClient, Mono<T>> read) {
    return credential(GovernedDocumentCredentialPort.Peer.PRODUCTS, tenant, contract, action)
        .flatMap(
            token -> {
              var format =
                  io.fireflysoftware.core.product.sdk.invoker.ApiClient.createDefaultDateFormat();
              var mapper =
                  GovernedPeerCodec.strict(
                      io.fireflysoftware.core.product.sdk.invoker.ApiClient.createDefaultMapper(
                          format));
              var web =
                  io.fireflysoftware.core.product.sdk.invoker.ApiClient.buildWebClientBuilder(
                          mapper)
                      .codecs(
                          c -> c.defaultCodecs().maxInMemorySize(GovernedPeerCodec.MAX_BODY_BYTES))
                      .build();
              var client =
                  new io.fireflysoftware.core.product.sdk.invoker.ApiClient(web, mapper, format)
                      .setBasePath(products.required());
              client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
              return read.apply(client);
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private <T> Mono<T> master(
      UUID tenant,
      UUID contract,
      UUID action,
      Function<io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient, Mono<T>>
          read) {
    return credential(GovernedDocumentCredentialPort.Peer.MASTERS, tenant, contract, action)
        .flatMap(
            token -> {
              var format =
                  io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient
                      .createDefaultDateFormat();
              var mapper =
                  GovernedPeerCodec.strict(
                      io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient
                          .createDefaultMapper(format));
              var web =
                  io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient
                      .buildWebClientBuilder(mapper)
                      .codecs(
                          c -> c.defaultCodecs().maxInMemorySize(GovernedPeerCodec.MAX_BODY_BYTES))
                      .build();
              var client =
                  new io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient(
                          web, mapper, format)
                      .setBasePath(masters.required());
              client.addDefaultHeader("Authorization", "Bearer " + token.bearer());
              return read.apply(client);
            })
        .transform(GovernedDocumentSdkAdapter::bounded);
  }

  private Mono<GovernedDocumentCredentialPort.Credential> credential(
      GovernedDocumentCredentialPort.Peer peer, UUID tenant, UUID contract, UUID action) {
    return Mono.defer(
        () -> {
          if (tenant == null || contract == null || action == null) return Mono.error(invalid());
          return credentials
              .current(peer, tenant, contract, action)
              .filter(
                  value ->
                      value != null
                          && value.bearer() != null
                          && value.bearer().length() <= 16384
                          && value.bearer().matches("[A-Za-z0-9._~+/=-]+")
                          && value.expiresAt() != null
                          && value.expiresAt().isAfter(clock.instant()))
              .switchIfEmpty(
                  Mono.error(
                      new ResponseStatusException(
                          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                          "GOVERNED_DOCUMENT_DELEGATION_REQUIRED")));
        });
  }

  private ContractDocumentSourceObservation observation(
      String system, String type, UUID resource, Object... projection) {
    try {
      String exact = FINGERPRINT_JSON.writeValueAsString(Arrays.asList(projection));
      String digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(exact.getBytes(StandardCharsets.UTF_8)));
      long at = clock.millis();
      if (at < -2208988800000L || at > 253402300799999L) throw invalid();
      return new ContractDocumentSourceObservation()
          .system(system)
          .resourceType(type)
          .resourceId(resource.toString())
          .sourceVersion("CONTRACT_OBSERVATION_V1:" + digest)
          .observedAtEpochMillis(at);
    } catch (Exception error) {
      throw invalid();
    }
  }

  private static String name(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.length() > max
        || !value.equals(value.strip())
        || value
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff || c == 0xfffd))
      throw invalid();
    return value;
  }

  private static void optionalName(List<String> names, String value) {
    if (value != null && !value.isBlank()) names.add(name(value, 200));
  }

  private static String date(LocalDateTime value) {
    if (value == null) return null;
    long epoch = value.toInstant(ZoneOffset.UTC).toEpochMilli();
    if (epoch < -2208988800000L || epoch > 253402300799999L) throw invalid();
    return value.toString();
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        org.springframework.http.HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_PEER_INVALID");
  }
}
