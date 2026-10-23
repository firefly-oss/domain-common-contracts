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

import io.fireflysoftware.domain.common.contracts.infra.documents.v1.GovernedDocumentCredentialPort;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Private signed qualification uses the real application security chain and an explicit trust SPI.
 */
public final class GovernedDocumentRuntimeBootstrap {
  public static void main(String[] args) throws Exception {
    new SpringApplicationBuilder(
            Class.forName(System.getProperty("contract.fixture.application")),
            SyntheticDelegation.class)
        .web(WebApplicationType.REACTIVE)
        .run(args);
  }

  @Configuration(proxyBeanMethods = false)
  static class SyntheticDelegation {
    @Bean
    @org.springframework.core.annotation.Order(-110)
    org.springframework.security.web.server.SecurityWebFilterChain fixtureDiscovery(
        org.springframework.security.config.web.server.ServerHttpSecurity http,
        @org.springframework.beans.factory.annotation.Value(
                "${firefly.contracts.documents.security.jwk-set-uri}")
            String jwks) {
      var decoder =
          org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(jwks)
              .build();
      decoder.setJwtValidator(
          org.fireflyframework.security.rs.AccessTokenValidators.forIssuerAndAudience(
              PrivateIssuer.ISSUER, "domain-contracts"));
      return http.securityMatcher(
              org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
                  .pathMatchers("/v3/api-docs", "/v3/api-docs.yaml"))
          .csrf(org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec::disable)
          .authorizeExchange(e -> e.anyExchange().hasAuthority("SCOPE_fixture.openapi.read"))
          .oauth2ResourceServer(o -> o.jwt(j -> j.jwtDecoder(decoder)))
          .build();
    }

    @Bean
    State fixtureState() {
      return new State();
    }

    @Bean
    Control fixtureControl(SecurityContextPort context, State state) {
      return new Control(context, state);
    }

    @Bean
    PolicyDecisionPort fixtureGovernedPolicy(State state) {
      return (p, action, resource, context) -> {
        String contract = String.valueOf(context.get("resourceId"));
        if (state.denied.contains(contract)
            || !p.hasScope(action)
            || !(p.claims().get("contract_document_contracts") instanceof Collection<?> ids)
            || !ids.contains(contract))
          return Mono.just(Decision.deny("Synthetic exact current contract denied"));
        return Mono.just(Decision.permit());
      };
    }

    @Bean
    GovernedDocumentCredentialPort fixtureGovernedCredentials(
        SecurityContextPort context, State state) {
      return (peer, tenant, contract, action) ->
          context
              .currentPrincipal()
              .filter(
                  p ->
                      tenant.toString().equals(p.tenantId())
                          && p.claims().get("contract_document_contracts")
                              instanceof Collection<?> ids
                          && ids.contains(contract.toString())
                          && !state.denied.contains(contract.toString()))
              .switchIfEmpty(
                  Mono.error(
                      new ResponseStatusException(
                          HttpStatus.FORBIDDEN, "Synthetic delegation denied")))
              .map(
                  p -> {
                    state
                        .calls
                        .computeIfAbsent(peer.name(), ignored -> new AtomicInteger())
                        .incrementAndGet();
                    Set<String> scopes =
                        switch (peer) {
                          case CONTRACTS ->
                              Set.of(
                                  "contracts.read",
                                  "contracts.prepare",
                                  "contracts.seal",
                                  "contracts.facts.read",
                                  "contracts.record-issuance",
                                  "contracts.attach");
                          case DOCUMENTS ->
                              Set.of(
                                  "documents.issue",
                                  "documents.read",
                                  "documents.content.read",
                                  "documents.retention");
                          case MASTERS -> Set.of("templates.read", "templates.resolve");
                          default -> Set.of("source.read");
                        };
                    Set<String> roles =
                        switch (peer) {
                          case CONTRACTS -> Set.of("contracts.document.fact-producer");
                          case DOCUMENTS -> Set.of("ecm.document.fact-producer");
                          default -> Set.of();
                        };
                    String audience =
                        switch (peer) {
                          case CONTRACTS -> "contracts";
                          case DOCUMENTS -> "document";
                          default -> peer.name().toLowerCase(Locale.ROOT);
                        };
                    String bearer =
                        ContractRuntimeSupport.token(
                            audience, tenant, scopes, roles, List.of(), Map.of());
                    return new GovernedDocumentCredentialPort.Credential(
                        bearer, Instant.now().plusSeconds(300));
                  });
    }
  }

  static final class State {
    final Set<String> denied = ConcurrentHashMap.newKeySet();
    final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
  }

  @RestController
  static final class Control {
    final SecurityContextPort context;
    final State state;

    Control(SecurityContextPort context, State state) {
      this.context = context;
      this.state = state;
    }

    @PostMapping(
        "/api/v1/tenants/{tenant}/contracts/{contract}/governed-documents/synthetic-policy")
    Mono<Map<String, Integer>> control(
        @PathVariable UUID tenant, @PathVariable UUID contract, @RequestParam boolean deny) {
      return context
          .currentPrincipal()
          .filter(p -> p.hasScope("fixture.admin") && tenant.toString().equals(p.tenantId()))
          .switchIfEmpty(
              Mono.error(
                  new ResponseStatusException(HttpStatus.FORBIDDEN, "Synthetic control denied")))
          .map(
              p -> {
                if (deny) state.denied.add(contract.toString());
                else state.denied.remove(contract.toString());
                var result = new LinkedHashMap<String, Integer>();
                state.calls.forEach((key, value) -> result.put(key, value.get()));
                return result;
              });
    }
  }
}
