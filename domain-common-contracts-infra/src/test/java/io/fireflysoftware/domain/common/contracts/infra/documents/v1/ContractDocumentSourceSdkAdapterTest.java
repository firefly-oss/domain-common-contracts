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

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class ContractDocumentSourceSdkAdapterTest {
  final UUID tenant = UUID.randomUUID(),
      contract = UUID.randomUUID(),
      action = UUID.randomUUID(),
      party = UUID.randomUUID(),
      profile = UUID.randomUUID(),
      product = UUID.randomUUID(),
      version = UUID.randomUUID(),
      role = UUID.randomUUID(),
      type = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");
  final Map<String, String> responses = new ConcurrentHashMap<>();
  final List<String> paths = new CopyOnWriteArrayList<>(), auth = new CopyOnWriteArrayList<>();
  HttpServer server;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          paths.add(path);
          auth.add(Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"), ""));
          byte[] body = responses.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    responses.put(
        "/api/v1/parties/" + party,
        "{\"partyId\":\""
            + party
            + "\",\"tenantId\":\""
            + tenant
            + "\",\"partyKind\":\"INDIVIDUAL\",\"updatedAt\":\"2026-10-21T10:00:00\"}");
    responses.put(
        "/api/v1/parties/" + party + "/natural-persons",
        "{\"naturalPersonId\":\""
            + profile
            + "\",\"partyId\":\""
            + party
            + "\",\"givenName\":\"María\",\"middleName\":\"Elena\",\"familyName\":\"García\",\"secondFamilyName\":\"López\",\"taxIdNumber\":\"synthetic-private-tax\",\"dateOfBirth\":\"1990-01-01\"}");
    responses.put(
        "/api/v1/parties/" + party + "/legal-entities",
        "{\"legalEntityId\":\""
            + profile
            + "\",\"partyId\":\""
            + party
            + "\",\"legalName\":\"Synthetic García & Sons LLC\",\"tradeName\":\"Unrelated display"
            + " alias\"}");
    responses.put(
        "/api/v1/products/" + product,
        "{\"productId\":\""
            + product
            + "\",\"tenantId\":\""
            + tenant
            + "\",\"productCode\":\"SYNTHETIC_LOAN\",\"productName\":\"Synthetic loan\"}");
    responses.put(
        "/api/v1/products/" + product + "/versions/" + version,
        "{\"productVersionId\":\""
            + version
            + "\",\"productId\":\""
            + product
            + "\",\"versionNumber\":9007199254740993,\"effectiveDate\":\"2025-01-01T00:00:00\"}");
    responses.put(
        "/api/v1/contract-roles/" + role,
        "{\"roleId\":\""
            + role
            + "\",\"roleCode\":\"BORROWER\",\"name\":\"Borrower\",\"isActive\":true}");
    responses.put(
        "/api/v1/contract-document-types/" + type,
        "{\"documentTypeId\":\""
            + type
            + "\",\"documentCode\":\"AGREEMENT\",\"name\":\"Agreement\",\"isActive\":true}");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  ContractDocumentSourceSdkAdapter adapter() {
    return adapter(
        (p, t, c, a) -> {
          assertThat(t).isEqualTo(tenant);
          assertThat(c).isEqualTo(contract);
          assertThat(a).isEqualTo(action);
          return Mono.just(
              new GovernedDocumentCredentialPort.Credential("synthetic-" + p, now.plusSeconds(60)));
        });
  }

  ContractDocumentSourceSdkAdapter adapter(GovernedDocumentCredentialPort credentials) {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return new ContractDocumentSourceSdkAdapter(
        new GovernedPeerEndpoint(base, true),
        new GovernedPeerEndpoint(base, true),
        new GovernedPeerEndpoint(base, true),
        credentials,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  void invalid(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
  }

  @Test
  void genuineCustomerSdkUsesActualPartyAndProfileWithMinimalUnicodeDisplay() {
    var value = adapter().party(tenant, contract, action, party).block();
    assertThat(value.getPartyId()).isEqualTo(party.toString());
    assertThat(value.getTenantId()).isEqualTo(tenant.toString());
    assertThat(value.getPartyType()).isEqualTo("INDIVIDUAL");
    assertThat(value.getDisplayName()).isEqualTo("María Elena García López");
    assertThat(value.toString()).doesNotContain("synthetic-private-tax", "1990-01-01");
    assertThat(value.getSource().getSourceVersion())
        .matches("CONTRACT_OBSERVATION_V1:[a-f0-9]{64}");
    assertThat(value.getSource().getObservedAtEpochMillis()).isEqualTo(now.toEpochMilli());
    assertThat(paths)
        .containsExactly(
            "/api/v1/parties/" + party, "/api/v1/parties/" + party + "/natural-persons");
    assertThat(auth).containsOnly("Bearer synthetic-CUSTOMERS");
  }

  @Test
  void legitimateGivenOnlyAndFamilyOnlyCustomerNamesRemainUsable() {
    String path = "/api/v1/parties/" + party + "/natural-persons", original = responses.get(path);
    responses.put(
        path,
        original
            .replace("\"familyName\":\"García\",", "")
            .replace("\"secondFamilyName\":\"López\",", "")
            .replace("\"middleName\":\"Elena\",", ""));
    assertThat(adapter().party(tenant, contract, action, party).block().getDisplayName())
        .isEqualTo("María");
    responses.put(
        path,
        original
            .replace("\"givenName\":\"María\",", "")
            .replace("\"secondFamilyName\":\"López\",", "")
            .replace("\"middleName\":\"Elena\",", ""));
    assertThat(adapter().party(tenant, contract, action, party).block().getDisplayName())
        .isEqualTo("García");
  }

  @Test
  void organizationUsesLegalNameAndDoesNotFallBackToTradeName() {
    String path = "/api/v1/parties/" + party;
    responses.put(path, responses.get(path).replace("INDIVIDUAL", "ORGANIZATION"));
    var value = adapter().party(tenant, contract, action, party).block();
    assertThat(value.getDisplayName()).isEqualTo("Synthetic García & Sons LLC");
    assertThat(paths.get(1)).endsWith("/legal-entities");
    responses.put(
        path + "/legal-entities",
        responses
            .get(path + "/legal-entities")
            .replace("\"legalName\":\"Synthetic García & Sons LLC\",", ""));
    invalid(() -> adapter().party(tenant, contract, action, party).block());
  }

  @Test
  void genuineProductAndMastersSdkKeepExactIdentityAndNumericVersion() {
    var p = adapter().product(tenant, contract, action, product, version).block();
    assertThat(p.getProductVersionId()).isEqualTo(version.toString());
    assertThat(p.getVersionNumber()).isEqualTo(9007199254740993L);
    assertThat(p.getVersionEffectiveDate()).isEqualTo("2025-01-01T00:00");
    assertThat(adapter().role(tenant, contract, action, role).block().getCode())
        .isEqualTo("BORROWER");
    assertThat(adapter().documentType(tenant, contract, action, type).block().getCode())
        .isEqualTo("AGREEMENT");
    assertThat(paths)
        .containsExactly(
            "/api/v1/products/" + product,
            "/api/v1/products/" + product + "/versions/" + version,
            "/api/v1/contract-roles/" + role,
            "/api/v1/contract-document-types/" + type);
    assertThat(auth)
        .containsExactly(
            "Bearer synthetic-PRODUCTS",
            "Bearer synthetic-PRODUCTS",
            "Bearer synthetic-MASTERS",
            "Bearer synthetic-MASTERS");
  }

  @Test
  void nativeSupportedPreEpochProductEffectiveDateRemainsValid() {
    String path = "/api/v1/products/" + product + "/versions/" + version;
    responses.put(path, responses.get(path).replace("2025-01-01T00:00:00", "1960-01-01T00:00:00"));
    assertThat(
            adapter()
                .product(tenant, contract, action, product, version)
                .block()
                .getVersionEffectiveDate())
        .isEqualTo("1960-01-01T00:00");
  }

  @Test
  void wrongPartyTenantStopsBeforeAnyProfileRead() {
    String path = "/api/v1/parties/" + party;
    responses.put(
        path, responses.get(path).replace(tenant.toString(), UUID.randomUUID().toString()));
    invalid(() -> adapter().party(tenant, contract, action, party).block());
    assertThat(paths).containsExactly(path);
  }

  @Test
  void wrongProfileParentAndWrongProductVersionParentReject() {
    String path = "/api/v1/parties/" + party + "/natural-persons";
    responses.put(
        path, responses.get(path).replace(party.toString(), UUID.randomUUID().toString()));
    invalid(() -> adapter().party(tenant, contract, action, party).block());
    path = "/api/v1/products/" + product + "/versions/" + version;
    responses.put(
        path, responses.get(path).replace(product.toString(), UUID.randomUUID().toString()));
    invalid(() -> adapter().product(tenant, contract, action, product, version).block());
  }

  @Test
  void fractionalVersionAndDuplicateOrUnknownResponseFieldsFailClosed() {
    String path = "/api/v1/products/" + product + "/versions/" + version;
    responses.put(path, responses.get(path).replace("9007199254740993", "1.5"));
    invalid(() -> adapter().product(tenant, contract, action, product, version).block());
    path = "/api/v1/contract-roles/" + role;
    String valid = responses.get(path);
    responses.put(path, valid.replace("true}", "true,\"isActive\":false}"));
    invalid(() -> adapter().role(tenant, contract, action, role).block());
    responses.put(path, valid.replace("true}", "true,\"unknown\":1}"));
    invalid(() -> adapter().role(tenant, contract, action, role).block());
  }

  @Test
  void inactiveMasterAndMissingRequiredNameNeverBecomeObservations() {
    String path = "/api/v1/contract-document-types/" + type;
    responses.put(path, responses.get(path).replace("true", "false"));
    invalid(() -> adapter().documentType(tenant, contract, action, type).block());
    path = "/api/v1/parties/" + party + "/natural-persons";
    responses.put(
        path,
        responses
            .get(path)
            .replace("\"givenName\":\"María\",", "")
            .replace("\"familyName\":\"García\",", ""));
    invalid(() -> adapter().party(tenant, contract, action, party).block());
  }

  @Test
  void observationFingerprintChangesWithConsumedSourceButNeverClaimsAnExternalRevision() {
    var first = adapter().party(tenant, contract, action, party).block();
    assertThat(
            adapter().party(tenant, contract, action, party).block().getSource().getSourceVersion())
        .isEqualTo(first.getSource().getSourceVersion());
    String path = "/api/v1/parties/" + party + "/natural-persons";
    responses.put(path, responses.get(path).replace("María", "Ana"));
    assertThat(
            adapter().party(tenant, contract, action, party).block().getSource().getSourceVersion())
        .isNotEqualTo(first.getSource().getSourceVersion());
  }

  @Test
  void missingOrExpiredDelegationHasNoHttpEffect() {
    assertThatThrownBy(
            () ->
                adapter((p, t, c, a) -> Mono.empty())
                    .party(tenant, contract, action, party)
                    .block())
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                adapter(
                        (p, t, c, a) ->
                            Mono.just(
                                new GovernedDocumentCredentialPort.Credential(
                                    "synthetic-expired", now)))
                    .role(tenant, contract, action, role)
                    .block())
        .isInstanceOf(ResponseStatusException.class);
    assertThat(paths).isEmpty();
  }

  @Test
  void legacyEndpointPropertiesCannotSelectGovernedSourceOwners() {
    try (var context =
        new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new org.springframework.core.env.MapPropertySource(
                  "synthetic-legacy",
                  Map.of(
                      "api-configuration.common-platform.customer-mgmt.base-path",
                      base,
                      "api-configuration.common-platform.product-mgmt.base-path",
                      base,
                      "api-configuration.common-platform.reference-master-data.base-path",
                      base)));
      context.registerBean(
          GovernedDocumentCredentialPort.class,
          () ->
              (p, t, id, a) ->
                  Mono.just(
                      new GovernedDocumentCredentialPort.Credential(
                          "synthetic-current", Instant.now().plusSeconds(60))));
      context.register(ContractDocumentSourceSdkAdapter.class);
      context.refresh();
      var adapter = context.getBean(ContractDocumentSourceSdkAdapter.class);
      assertThatThrownBy(() -> adapter.party(tenant, contract, action, party).block())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
      assertThat(paths).isEmpty();
    }
  }
}
