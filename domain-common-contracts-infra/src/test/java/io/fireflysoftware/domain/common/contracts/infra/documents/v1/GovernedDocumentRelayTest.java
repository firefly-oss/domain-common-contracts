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

import java.time.*;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GovernedDocumentRelayTest {
  final UUID tenant = UUID.randomUUID(), contract = UUID.randomUUID(), action = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");

  Jwt jwt(String audience, Instant expiry) {
    return Jwt.withTokenValue("synthetic.signed.token")
        .header("alg", "RS256")
        .issuer("https://synthetic.invalid/issuer")
        .subject("synthetic-owner")
        .audience(List.of("domain-contracts", audience))
        .issuedAt(now.minusSeconds(30))
        .expiresAt(expiry)
        .claim("tenant_id", tenant.toString())
        .build();
  }

  SecurityPrincipal principal(Jwt jwt) {
    return SecurityPrincipal.builder()
        .subject(jwt.getSubject())
        .issuer(jwt.getIssuer().toString())
        .tenantId(tenant.toString())
        .claims(jwt.getClaims())
        .build();
  }

  GovernedDocumentRelay relay(String mode, SecurityPrincipal p) {
    var beans = new StaticListableBeanFactory();
    if (p != null) beans.addBean("context", (SecurityContextPort) () -> Mono.just(p));
    return new GovernedDocumentRelay(
        mode,
        Map.of(GovernedDocumentCredentialPort.Peer.CONTRACTS, "core-contracts"),
        beans.getBeanProvider(SecurityContextPort.class),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  Mono<GovernedDocumentCredentialPort.Credential> token(
      GovernedDocumentRelay relay, JwtAuthenticationToken authentication) {
    return relay
        .current(GovernedDocumentCredentialPort.Peer.CONTRACTS, tenant, contract, action)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
  }

  JwtAuthenticationToken auth(Jwt jwt, SecurityPrincipal principal) {
    return new JwtAuthenticationToken(jwt, principal, List.of());
  }

  void denied(Mono<?> work) {
    assertThatThrownBy(work::block).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void explicitMultiAudienceModeReturnsOnlyCurrentValidatedCredential() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var principal = principal(jwt);
    var credential =
        token(relay("validated-multi-audience", principal), auth(jwt, principal)).block();
    assertThat(credential.bearer()).isEqualTo(jwt.getTokenValue());
    assertThat(credential.expiresAt()).isEqualTo(jwt.getExpiresAt());
    assertThat(credential.toString())
        .isEqualTo("GovernedDocumentCredential[redacted]")
        .doesNotContain(jwt.getTokenValue());
  }

  @Test
  void unspecifiedModeDoesNotRelayEvenAuthenticatedBearer() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var p = principal(jwt);
    denied(token(relay("", p), auth(jwt, p)));
  }

  @Test
  void absentReactiveAuthenticationDenies() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    denied(
        relay("validated-multi-audience", principal(jwt))
            .current(GovernedDocumentCredentialPort.Peer.CONTRACTS, tenant, contract, action));
  }

  @Test
  void plainJwtAuthenticationWithoutRichTrustedPrincipalDenies() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    denied(
        token(relay("validated-multi-audience", principal(jwt)), new JwtAuthenticationToken(jwt)));
  }

  @Test
  void missingPeerAudienceDenies() {
    Jwt jwt = jwt("other-core", now.plusSeconds(60));
    var p = principal(jwt);
    denied(token(relay("validated-multi-audience", p), auth(jwt, p)));
  }

  @Test
  void expiredBearerDenies() {
    Jwt jwt = jwt("core-contracts", now);
    var p = principal(jwt);
    denied(token(relay("validated-multi-audience", p), auth(jwt, p)));
  }

  @Test
  void mismatchBetweenJaflyContextAndValidatedAuthenticationDenies() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var p = principal(jwt);
    denied(
        token(
            relay("validated-multi-audience", p.toBuilder().subject("different-owner").build()),
            auth(jwt, p)));
  }

  @Test
  void bodyTenantCannotOverrideSignedPrincipalTenant() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var p = principal(jwt).toBuilder().tenantId(UUID.randomUUID().toString()).build();
    denied(token(relay("validated-multi-audience", p), auth(jwt, p)));
  }

  @Test
  void unconfiguredPeerDenies() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var p = principal(jwt);
    denied(
        relay("validated-multi-audience", p)
            .current(GovernedDocumentCredentialPort.Peer.DOCUMENTS, tenant, contract, action)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(jwt, p))));
  }

  @Test
  void principalCannotInventClaimsDifferentFromActualJwt() {
    Jwt jwt = jwt("core-contracts", now.plusSeconds(60));
    var p =
        principal(jwt).toBuilder()
            .claims(Map.of("exp", now.plusSeconds(60), "aud", List.of("core-contracts")))
            .build();
    denied(token(relay("validated-multi-audience", p), auth(jwt, p)));
  }
}
