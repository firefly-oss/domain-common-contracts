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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GovernedDocumentDelegationConfigurationTest {
  final UUID tenant = UUID.randomUUID(), contract = UUID.randomUUID(), action = UUID.randomUUID();
  final Jwt jwt =
      Jwt.withTokenValue("synthetic.signed.token")
          .header("alg", "RS256")
          .issuer("https://synthetic.invalid/issuer")
          .subject("synthetic-owner")
          .audience(List.of("domain-contracts", "core-contracts"))
          .issuedAt(Instant.now().minusSeconds(30))
          .expiresAt(Instant.now().plusSeconds(120))
          .claim("tenant_id", tenant.toString())
          .build();
  final SecurityPrincipal principal =
      SecurityPrincipal.builder()
          .subject(jwt.getSubject())
          .issuer(jwt.getIssuer().toString())
          .tenantId(tenant.toString())
          .claims(jwt.getClaims())
          .build();
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  GovernedDocumentDelegationConfiguration.class))
          .withBean(SecurityContextPort.class, () -> () -> Mono.just(principal));

  Mono<GovernedDocumentCredentialPort.Credential> current(GovernedDocumentCredentialPort port) {
    return port.current(GovernedDocumentCredentialPort.Peer.CONTRACTS, tenant, contract, action)
        .contextWrite(
            ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, principal, List.of())));
  }

  @Test
  void ordinaryConfigurationFailsClosedEvenWithAValidAuthentication() {
    runner.run(
        context ->
            assertThatThrownBy(
                    () -> current(context.getBean(GovernedDocumentCredentialPort.class)).block())
                .isInstanceOfSatisfying(
                    ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(503)));
  }

  @Test
  void explicitModeAndPeerAudienceEnableCurrentValidatedRelay() {
    runner
        .withPropertyValues(
            "firefly.contracts.documents.delegation.mode=validated-multi-audience",
            "firefly.contracts.documents.peers.contracts.audience=core-contracts")
        .run(
            context ->
                assertThat(
                        current(context.getBean(GovernedDocumentCredentialPort.class))
                            .block()
                            .bearer())
                    .isEqualTo(jwt.getTokenValue()));
  }

  @Test
  void aTrustedCustomCredentialAdapterReplacesTheDefault() {
    GovernedDocumentCredentialPort custom =
        (p, t, c, a) ->
            Mono.just(
                new GovernedDocumentCredentialPort.Credential(
                    "synthetic.custom.token", Instant.now().plusSeconds(30)));
    runner
        .withBean(GovernedDocumentCredentialPort.class, () -> custom)
        .run(
            context ->
                assertThat(context.getBean(GovernedDocumentCredentialPort.class)).isSameAs(custom));
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  static class ProjectCredentials {
    @org.springframework.context.annotation.Bean
    GovernedDocumentCredentialPort projectCredentials() {
      return (p, t, c, a) ->
          Mono.just(
              new GovernedDocumentCredentialPort.Credential(
                  "synthetic.project.token", Instant.now().plusSeconds(30)));
    }
  }

  @Test
  void actualUserConfigurationRegistersBeforeConditionalDefault() {
    runner
        .withUserConfiguration(ProjectCredentials.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(GovernedDocumentCredentialPort.class);
              assertThat(
                      current(context.getBean(GovernedDocumentCredentialPort.class))
                          .block()
                          .bearer())
                  .isEqualTo("synthetic.project.token");
            });
  }
}
