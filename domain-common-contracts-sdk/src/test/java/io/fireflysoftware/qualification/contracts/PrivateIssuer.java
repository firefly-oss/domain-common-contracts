/*
 * Copyright 2025 Firefly Software Foundation
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

package io.fireflysoftware.qualification.contracts;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

final class PrivateIssuer implements AutoCloseable {
  static final String ISSUER = "https://synthetic-governed-domain.invalid";
  private final RSAKey key;
  private final HttpServer server;

  PrivateIssuer() throws Exception {
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
          exchange.close();
        });
    server.start();
  }

  String jwks() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
  }

  String token(UUID tenant, List<UUID> contracts, Set<String> scopes) {
    try {
      var claims =
          new JWTClaimsSet.Builder()
              .issuer(ISSUER)
              .subject("synthetic-governed-domain-owner")
              .audience("domain-contracts")
              .issueTime(Date.from(Instant.now().minusSeconds(1)))
              .expirationTime(Date.from(Instant.now().plusSeconds(300)))
              .claim("tenant_id", tenant.toString())
              .claim("scope", String.join(" ", scopes))
              .claim("contract_document_contracts", contracts.stream().map(UUID::toString).toList())
              .build();
      var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
