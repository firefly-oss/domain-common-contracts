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

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.commons.ecm.sdk.model.*;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.core.documents.KitDocumentService;
import io.fireflysoftware.domain.common.contracts.core.documents.LegacyDocumentMigration;
import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedContractDocumentCoordinator;
import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedDocumentAccessGuard;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.*;
import io.fireflysoftware.domain.common.contracts.web.controllers.ContractDocumentController;
import io.fireflysoftware.domain.common.contracts.web.controllers.KitDocumentsController;
import io.fireflysoftware.domain.common.contracts.web.controllers.document.v1.GovernedContractDocumentController;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.AccessTokenValidators;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = GovernedDocumentSecurityHttpTest.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "server.address=127.0.0.1",
      "firefly.eda.enabled=false",
      "firefly.stepevents.enabled=false",
      "firefly.contracts.documents.delegation.mode=validated-multi-audience",
      "firefly.contracts.documents.peers.contracts.audience=core-contracts",
      "firefly.web.idempotency.enabled=true",
      "firefly.web.idempotency.namespace=governed-contract-test"
    })
class GovernedDocumentSecurityHttpTest {
  static final UUID TENANT = UUID.randomUUID(),
      CONTRACT = UUID.randomUUID(),
      ACTION = UUID.randomUUID();
  static final String ISSUER = "https://synthetic.document.issuer.invalid";
  static final AtomicReference<ContractDocumentOperation> RETAINED = new AtomicReference<>();
  static final AtomicBoolean LEGACY_MIGRATED = new AtomicBoolean(true);
  static final AtomicBoolean PERMITTED = new AtomicBoolean(true);
  static final AtomicInteger JWKS_READS = new AtomicInteger();
  static final RSAKey KEY;
  static final HttpServer JWKS;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("synthetic-document-key").generate();
      JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      JWKS.createContext(
          "/jwks",
          exchange -> {
            JWKS_READS.incrementAndGet();
            byte[] bytes =
                new JWKSet(KEY.toPublicJWK())
                    .toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
              body.write(bytes);
            }
          });
      JWKS.start();
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  @DynamicPropertySource
  static void trust(DynamicPropertyRegistry properties) {
    properties.add("firefly.contracts.documents.security.issuer", () -> ISSUER);
    properties.add("firefly.contracts.documents.security.audience", () -> "domain-contracts");
    properties.add(
        "firefly.contracts.documents.security.jwk-set-uri",
        () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
  }

  @AfterAll
  static void stop() {
    JWKS.stop(0);
  }

  @BeforeEach
  void reset() {
    PERMITTED.set(true);
    LEGACY_MIGRATED.set(true);
    RETAINED.set(null);
  }

  @LocalServerPort int port;

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    GovernedDocumentSecurityConfiguration.class,
    GovernedDocumentAccessGuard.class,
    GovernedDocumentDelegationConfiguration.class,
    Probe.class,
    KitDocumentsController.class,
    ContractDocumentController.class,
    GovernedContractDocumentController.class
  })
  static class Application {
    @Bean
    GovernedContractDocumentCoordinator governedCoordinator(
        GovernedDocumentAccessGuard guard, GovernedDocumentCredentialPort credentials) {
      var peers =
          new GovernedDocumentPeerPort() {
            public Mono<ContractDocumentOperation> prepare(
                UUID t, UUID c, ContractDocumentPrepareRequest intent) {
              return credentials
                  .current(
                      GovernedDocumentCredentialPort.Peer.CONTRACTS,
                      t,
                      c,
                      UUID.fromString(intent.getActionId()))
                  .map(
                      credential -> {
                        var value =
                            new ContractDocumentOperation()
                                .operationId(UUID.randomUUID().toString())
                                .tenantId(t.toString())
                                .contractId(c.toString())
                                .actionId(intent.getActionId())
                                .intent(intent)
                                .sourceRevision(intent.getExpectedContractRevision())
                                .phase(ContractDocumentOperation.PhaseEnum.ATTACHED)
                                .coreSnapshotDigest("c".repeat(64))
                                .sealedRequestDigest("d".repeat(64))
                                .factsDigest("e".repeat(64))
                                .attachmentId(UUID.randomUUID().toString())
                                .issued(
                                    new ContractDocumentIssuedEvidence()
                                        .issuanceId(UUID.randomUUID().toString())
                                        .documentId(UUID.randomUUID().toString())
                                        .documentVersionId(UUID.randomUUID().toString())
                                        .versionNumber(1)
                                        .mediaType("text/html")
                                        .byteLength(1L)
                                        .contentDigest("a".repeat(64))
                                        .factsDigest("e".repeat(64))
                                        .receiptDigest("b".repeat(64)));
                        RETAINED.set(value);
                        return value;
                      });
            }

            public Mono<ContractDocumentOperation> get(UUID t, UUID c, UUID a) {
              return Mono.justOrEmpty(RETAINED.get());
            }

            public Mono<ContractDocumentFactPreparation> facts(UUID t, UUID c, UUID a) {
              return Mono.error(
                  new AssertionError(
                      "No source collection in this current-authority HTTP fixture"));
            }

            public Mono<ContractDocumentOperation> seal(
                UUID t, UUID c, UUID a, ContractDocumentEnrichment e) {
              return Mono.error(new AssertionError());
            }

            public Mono<DocumentIssuanceReceipt> issue(
                UUID t, UUID c, UUID a, DocumentIssueRequest r) {
              return Mono.error(new AssertionError());
            }

            public Mono<ContractDocumentOperation> record(UUID t, UUID c, UUID a, UUID i) {
              return Mono.error(new AssertionError());
            }

            public Mono<ContractDocumentOperation> attach(UUID t, UUID c, UUID a) {
              return Mono.error(new AssertionError());
            }

            public Mono<DocumentIssuedContent> content(UUID t, UUID c, UUID a, UUID i) {
              return Mono.error(new AssertionError());
            }
          };
      return new GovernedContractDocumentCoordinator(
          guard, peers, (t, c, a, captured) -> Mono.error(new AssertionError()));
    }

    @Bean
    KitDocumentService legacyKit() {
      return new KitDocumentService(null) {
        @Override
        public Mono<UUID> issue(String template, UUID party, UUID contract) {
          return LEGACY_MIGRATED.get() ? super.issue(template, party, contract) : Mono.just(ACTION);
        }
      };
    }

    @Bean
    ContractService legacyContracts() {
      var service = org.mockito.Mockito.mock(ContractService.class);
      org.mockito.Mockito.when(
              service.generateContractDocument(
                  org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
          .thenAnswer(
              invocation ->
                  LEGACY_MIGRATED.get()
                      ? Mono.error(LegacyDocumentMigration.required())
                      : Mono.just(ACTION));
      org.mockito.Mockito.when(service.getContractDocuments(org.mockito.ArgumentMatchers.any()))
          .thenReturn(Mono.just(List.of("synthetic-sensitive-legacy-association")));
      return service;
    }

    @Bean
    @Order(-101)
    SecurityWebFilterChain signedLegacyFixture(ServerHttpSecurity http, PrincipalFactory factory) {
      var decoder =
          NimbusReactiveJwtDecoder.withJwkSetUri(
                  "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks")
              .build();
      decoder.setJwtValidator(
          AccessTokenValidators.forIssuerAndAudience(ISSUER, "domain-contracts"));
      var authentication = new GovernedDocumentAuthentication(factory, Clock.systemUTC());
      return http.securityMatcher(
              ServerWebExchangeMatchers.pathMatchers(
                  "/api/v1/kit-documents",
                  "/api/v1/kit-documents/**",
                  "/api/v1/contracts/*/documents/generate"))
          .csrf(ServerHttpSecurity.CsrfSpec::disable)
          .authorizeExchange(e -> e.anyExchange().authenticated())
          .oauth2ResourceServer(
              resource ->
                  resource.jwt(
                      jwt ->
                          jwt.jwtDecoder(decoder)
                              .jwtAuthenticationConverter(
                                  token ->
                                      authentication
                                          .convert(token)
                                          .cast(AbstractAuthenticationToken.class))))
          .build();
    }

    @Bean
    PolicyDecisionPort exactSyntheticPolicy() {
      return (principal, action, resource, context) ->
          Mono.just(
              PERMITTED.get()
                      && principal.hasScope(action)
                      && principal.claims().get("contract_document_contracts")
                          instanceof List<?> ids
                      && ids.contains(context.get("resourceId"))
                  ? Decision.permit()
                  : Decision.deny("synthetic current resource denial"));
    }
  }

  @RestController
  static class Probe {
    final GovernedDocumentAccessGuard guard;
    final GovernedDocumentCredentialPort credentials;

    Probe(GovernedDocumentAccessGuard guard, GovernedDocumentCredentialPort credentials) {
      this.guard = guard;
      this.credentials = credentials;
    }

    @GetMapping(
        "/api/v1/tenants/{tenant}/contracts/{contract}/governed-documents/{action}/authority-probe")
    @DisableIdempotency
    Mono<String> probe(
        @PathVariable UUID tenant, @PathVariable UUID contract, @PathVariable UUID action) {
      return guard
          .authorize(tenant, contract, action, "read", Map.of())
          .flatMap(
              principal ->
                  credentials
                      .current(
                          GovernedDocumentCredentialPort.Peer.CONTRACTS, tenant, contract, action)
                      .map(credential -> principal.subject()));
    }
  }

  String token(
      String issuer,
      List<String> audiences,
      String tenant,
      boolean expiration,
      String scope,
      RSAKey key)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("synthetic-current-owner")
            .audience(audiences)
            .issueTime(Date.from(Instant.now().minusSeconds(5)))
            .claim("tenant_id", tenant)
            .claim("scope", scope)
            .claim("contract_document_contracts", List.of(CONTRACT.toString()));
    if (expiration) claims.expirationTime(Date.from(Instant.now().plusSeconds(120)));
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
            claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  String valid() throws Exception {
    return token(
        ISSUER,
        List.of("domain-contracts", "core-contracts"),
        TENANT.toString(),
        true,
        "contracts.document.read contracts.document.issue",
        KEY);
  }

  HttpResponse<String> get(UUID tenant, UUID contract, String token, String idempotency)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/tenants/"
                        + tenant
                        + "/contracts/"
                        + contract
                        + "/governed-documents/"
                        + ACTION
                        + "/authority-probe"))
            .timeout(Duration.ofSeconds(10));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (idempotency != null) request.header("X-Idempotency-Key", idempotency);
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
        .send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> legacyPost(String path, String body, String key) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + valid())
            .header("Content-Type", "application/json")
            .header("X-Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void legacyKitMigrationCannotReplayAnEarlierMetadataOnlySuccess() throws Exception {
    String
        body =
            "{\"templateCode\":\"OLD_CONTRACT\",\"partyId\":\""
                + TENANT
                + "\",\"contractId\":\""
                + CONTRACT
                + "\"}",
        key = UUID.randomUUID().toString();
    LEGACY_MIGRATED.set(false);
    assertThat(legacyPost("/api/v1/kit-documents", body, key).statusCode()).isEqualTo(200);
    LEGACY_MIGRATED.set(true);
    var migrated = legacyPost("/api/v1/kit-documents", body, key);
    assertThat(migrated.statusCode()).isEqualTo(409);
    assertThat(migrated.body()).contains("GOVERNED_DOCUMENT_INTENT_REQUIRED");
  }

  @Test
  void legacyGenerateMigrationCannotReplayAnEarlierMetadataOnlySuccess() throws Exception {
    String body = "{\"contractId\":\"" + CONTRACT + "\",\"templateId\":\"" + ACTION + "\"}",
        key = UUID.randomUUID().toString();
    String path = "/api/v1/contracts/" + CONTRACT + "/documents/generate";
    LEGACY_MIGRATED.set(false);
    assertThat(legacyPost(path, body, key).statusCode()).isEqualTo(202);
    LEGACY_MIGRATED.set(true);
    var migrated = legacyPost(path, body, key);
    assertThat(migrated.statusCode()).isEqualTo(409);
    assertThat(migrated.body()).contains("GOVERNED_DOCUMENT_INTENT_REQUIRED");
  }

  @Test
  void unmatchedLegacyCollectionDeniesBeforeSensitiveServiceResults() throws Exception {
    String path = "http://127.0.0.1:" + port + "/api/v1/contracts/" + CONTRACT + "/documents";
    for (boolean signed : List.of(false, true)) {
      var request = HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(10));
      if (signed) request.header("Authorization", "Bearer " + valid());
      var response =
          HttpClient.newHttpClient()
              .send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(response.body()).doesNotContain("synthetic-sensitive-legacy-association");
    }
  }

  @Test
  void legacyReadAlwaysRequiresMigrationOverActualHttp() throws Exception {
    String path = "http://127.0.0.1:" + port + "/api/v1/kit-documents/" + UUID.randomUUID();
    for (boolean signed : List.of(false, true)) {
      var request = HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(10));
      if (signed) request.header("Authorization", "Bearer " + valid());
      var response =
          HttpClient.newHttpClient()
              .send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(signed ? 409 : 401);
      if (signed) assertThat(response.body()).contains("GOVERNED_DOCUMENT_INTENT_REQUIRED");
    }
  }

  @Test
  void nativeIssueRechecksCurrentPolicyAfterSuccessfulSameKeyRequest() throws Exception {
    var input = new GovernedContractDocumentInputsTest();
    String body = input.valid().replace(input.action, ACTION.toString());
    String key = UUID.randomUUID().toString(),
        path = "/api/v1/tenants/" + TENANT + "/contracts/" + CONTRACT + "/governed-documents";
    var first = legacyPost(path, body, key);
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.body()).contains("ATTACHED");
    var retained = RETAINED.get();
    PERMITTED.set(false);
    var denied = legacyPost(path, body, key);
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(RETAINED.get()).isSameAs(retained);
  }

  @Test
  void actualRemoteJwksSignedTokenReachesJaflyPolicyAndCurrentRelay() throws Exception {
    var response = get(TENANT, CONTRACT, valid(), null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("synthetic-current-owner");
    assertThat(JWKS_READS).hasPositiveValue();
  }

  @Test
  void unsignedAndMissingExpirationNeverReachResource() throws Exception {
    assertThat(get(TENANT, CONTRACT, null, null).statusCode()).isEqualTo(401);
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        ISSUER,
                        List.of("domain-contracts", "core-contracts"),
                        TENANT.toString(),
                        false,
                        "contracts.document.read",
                        KEY),
                    null)
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void wrongIssuerAudienceAndSignatureAreRejected() throws Exception {
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        "https://other.invalid",
                        List.of("domain-contracts", "core-contracts"),
                        TENANT.toString(),
                        true,
                        "contracts.document.read",
                        KEY),
                    null)
                .statusCode())
        .isEqualTo(401);
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        ISSUER,
                        List.of("other-domain", "core-contracts"),
                        TENANT.toString(),
                        true,
                        "contracts.document.read",
                        KEY),
                    null)
                .statusCode())
        .isEqualTo(401);
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        ISSUER,
                        List.of("domain-contracts", "core-contracts"),
                        TENANT.toString(),
                        true,
                        "contracts.document.read",
                        new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate()),
                    null)
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void exactTenantContractScopeAndPeerAudienceAreAllRequired() throws Exception {
    assertThat(get(UUID.randomUUID(), CONTRACT, valid(), null).statusCode()).isEqualTo(403);
    assertThat(get(TENANT, UUID.randomUUID(), valid(), null).statusCode()).isEqualTo(403);
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        ISSUER,
                        List.of("domain-contracts", "core-contracts"),
                        TENANT.toString(),
                        true,
                        "unrelated.scope",
                        KEY),
                    null)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            get(
                    TENANT,
                    CONTRACT,
                    token(
                        ISSUER,
                        List.of("domain-contracts"),
                        TENANT.toString(),
                        true,
                        "contracts.document.read",
                        KEY),
                    null)
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void policyWithdrawalAfterWarmReadCannotReuseEarlierAuthority() throws Exception {
    String token = valid(), key = "synthetic-repeated-key";
    assertThat(get(TENANT, CONTRACT, token, key).statusCode()).isEqualTo(200);
    PERMITTED.set(false);
    assertThat(get(TENANT, CONTRACT, token, key).statusCode()).isEqualTo(403);
  }
}
