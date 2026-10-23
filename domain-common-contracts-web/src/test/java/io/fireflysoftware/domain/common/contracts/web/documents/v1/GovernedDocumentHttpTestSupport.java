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

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedDocumentAccessGuard;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

final class GovernedDocumentHttpTestSupport implements AutoCloseable {
  static final UUID TENANT = UUID.randomUUID(),
      CONTRACT = UUID.randomUUID(),
      ACTION = UUID.randomUUID();
  static final String ISSUER = "https://synthetic-strict-document.invalid";
  static final AtomicInteger RESOURCE_READS = new AtomicInteger(),
      POLICY_CALLS = new AtomicInteger();
  private final RSAKey key;
  private final HttpServer server;

  GovernedDocumentHttpTestSupport() {
    try {
      key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  String jwks() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
  }

  HttpResponse<String> get(int port) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject("synthetic-strict-document-owner")
            .audience("domain-contracts")
            .issueTime(Date.from(Instant.now().minusSeconds(1)))
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .claim("tenant_id", TENANT.toString())
            .claim("scope", "contracts.document.read")
            .claim("contract_document_contracts", List.of(CONTRACT.toString()))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://127.0.0.1:"
                            + port
                            + "/api/v1/tenants/"
                            + TENANT
                            + "/contracts/"
                            + CONTRACT
                            + "/governed-documents/"
                            + ACTION
                            + "/policy-probe"))
                .header("Authorization", "Bearer " + jwt.serialize())
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Override
  public void close() {
    server.stop(0);
  }

  @RestController
  static class GuardProbe {
    private final GovernedDocumentAccessGuard guard;

    GuardProbe(GovernedDocumentAccessGuard guard) {
      this.guard = guard;
    }

    @GetMapping(
        "/api/v1/tenants/{tenant}/contracts/{contract}/governed-documents/{action}/policy-probe")
    Mono<String> read(
        @PathVariable UUID tenant, @PathVariable UUID contract, @PathVariable UUID action) {
      return guard
          .authorize(tenant, contract, action, "read", Map.of())
          .map(
              principal -> {
                RESOURCE_READS.incrementAndGet();
                return "synthetic-private-document";
              });
    }
  }
}
