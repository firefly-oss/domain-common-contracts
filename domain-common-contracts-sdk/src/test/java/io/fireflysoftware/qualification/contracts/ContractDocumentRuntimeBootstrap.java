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

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/** Explicit synthetic signed trust loaded only by the private qualification process. */
public final class ContractDocumentRuntimeBootstrap {
  public static void main(String[] args) throws Exception {
    new SpringApplicationBuilder(
            Class.forName(System.getProperty("contract.fixture.application")),
            SyntheticSignedTrust.class)
        .web(WebApplicationType.REACTIVE)
        .run(args);
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  static class SyntheticSignedTrust {
    @Bean
    org.fireflyframework.web.idempotency.scope.IdempotencyScopeResolver fixtureCacheScope(
        SecurityContextPort context) {
      return exchange ->
          context
              .currentPrincipal()
              .map(
                  p ->
                      p.issuer()
                          + ":"
                          + p.subject()
                          + ":"
                          + p.tenantId()
                          + ":"
                          + p.scopes().stream()
                              .sorted()
                              .collect(java.util.stream.Collectors.joining(","))
                          + ":"
                          + p.authorities().stream()
                              .sorted()
                              .collect(java.util.stream.Collectors.joining(",")));
    }

    @Bean
    SyntheticCacheControl fixtureCacheControl(SecurityContextPort context) {
      return new SyntheticCacheControl(context);
    }

    @Bean
    SecurityContextPort fixtureContext() {
      return () ->
          Mono.deferContextual(
              c -> Mono.justOrEmpty(c.<SecurityPrincipal>getOrEmpty(SecurityPrincipal.class)));
    }

    @Bean
    PolicyState fixturePolicyState() {
      return new PolicyState();
    }

    @Bean
    SyntheticPolicyControl fixturePolicyControl(SecurityContextPort context, PolicyState state) {
      return new SyntheticPolicyControl(context, state);
    }

    @Bean
    PolicyDecisionPort fixturePolicy(PolicyState state) {
      return (p, a, r, c) -> {
        if (state.denied.stream().anyMatch(id -> r.endsWith("/" + id)))
          return Mono.just(Decision.deny("Synthetic current resource denied"));
        String scope =
            switch (a) {
              case "contracts.document.read" -> "contracts.read";
              case "contracts.document.write" -> "contracts.write";
              case "contracts.document.prepare" -> "contracts.prepare";
              case "contracts.document.seal" -> "contracts.seal";
              case "contracts.document.facts.read" -> "contracts.facts.read";
              case "contracts.document.record-issuance" -> "contracts.record-issuance";
              case "contracts.document.attach" -> "contracts.attach";
              case "contracts.document.ownership.bind" -> "contracts.ownership.bind";
              case "masters.document-template.read" -> "templates.read";
              case "masters.document-template.resolve" -> "templates.resolve";
              case "masters.document-template.publish",
                  "masters.document-template.retire",
                  "masters.document-template.draft.write" ->
                  "templates.admin";
              case "config.document-template-binding.read" -> "bindings.read";
              case "config.document-template-binding.resolve" -> "bindings.resolve";
              case "config.document-template-binding.write",
                  "config.document-template-binding.activate",
                  "config.document-template-binding.retire",
                  "config.document-template-binding.replace" ->
                  "bindings.admin";
              case "ecm.document.issue", "ecm.document.correct" -> "documents.issue";
              case "ecm.document.read", "ecm.document.legacy.read" -> "documents.read";
              case "ecm.document.content.read", "ecm.document.legacy.content.read" ->
                  "documents.content.read";
              case "ecm.document.legacy.write" -> "documents.legacy.write";
              case "ecm.document.retention.bind" -> "documents.retention";
              default -> null;
            };
        if (scope == null || !p.hasScope(scope))
          return Mono.just(Decision.deny("Synthetic scope denied"));
        if (Set.of("ecm.document.issue", "ecm.document.correct").contains(a)
            && (!"CONTRACT_DOCUMENT_V1".equals(c.get("factsContractCode"))
                || !"1".equals(c.get("factsContractVersion"))))
          return Mono.just(Decision.deny("Unknown synthetic facts contract"));
        if (a.equals("ecm.document.retention.bind")
            && (!"CONTRACT_EVIDENCE".equals(c.get("retentionPolicyCode"))
                || !"1".equals(c.get("retentionPolicyVersion"))))
          return Mono.just(Decision.deny("Unknown synthetic retention policy"));
        Object restricted = p.attributes().get("resourceRestriction");
        if (a.startsWith("ecm.document.")
            && restricted instanceof String id
            && !id.isBlank()
            && !r.endsWith("/" + id)) return Mono.just(Decision.deny("Synthetic resource denied"));
        return Mono.just(Decision.permit());
      };
    }

    @Bean
    @org.springframework.core.annotation.Order(-90)
    WebFilter signedFixtureAuthentication() {
      return (exchange, chain) -> {
        var principal = validate(exchange.getRequest().getHeaders().getFirst("Authorization"));
        return principal == null
            ? chain.filter(exchange)
            : chain.filter(exchange).contextWrite(c -> c.put(SecurityPrincipal.class, principal));
      };
    }

    private SecurityPrincipal validate(String authorization) {
      try {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String[] parts = authorization.substring(7).split("\\.", -1);
        if (parts.length != 3) return null;
        var json = JsonMapper.builder().build();
        var decoder = Base64.getUrlDecoder();
        var header = json.readTree(decoder.decode(parts[0]));
        if (!"HS256".equals(header.path("alg").asText())
            || !"JWT".equals(header.path("typ").asText())) return null;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
            new SecretKeySpec(
                Base64.getDecoder().decode(System.getenv("CONTRACT_FIXTURE_SIGNING_KEY")),
                "HmacSHA256"));
        if (!MessageDigest.isEqual(
            mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)),
            decoder.decode(parts[2]))) return null;
        var claims = json.readTree(decoder.decode(parts[1]));
        long now = java.time.Instant.now().getEpochSecond();
        if (!"synthetic-document-authority".equals(claims.path("iss").asText())
            || !System.getProperty("contract.fixture.audience").equals(claims.path("aud").asText())
            || !claims.path("exp").isIntegralNumber()
            || claims.path("exp").asLong() <= now
            || claims.path("nbf").asLong() > now
            || claims.path("sub").asText().isBlank()) return null;
        Set<String> scopes = new HashSet<>(), authorities = new HashSet<>();
        claims.path("scopes").forEach(v -> scopes.add(v.asText()));
        claims.path("authorities").forEach(v -> authorities.add(v.asText()));
        List<String> tenants = new ArrayList<>();
        claims.path("delegatedTenants").forEach(v -> tenants.add(v.asText()));
        return SecurityPrincipal.builder()
            .subject(claims.path("sub").asText())
            .issuer("synthetic-document-authority")
            .tenantId(claims.path("tenant").isTextual() ? claims.path("tenant").asText() : null)
            .claims(
                json.convertValue(
                    claims,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
            .scopes(scopes)
            .authorities(authorities)
            .attributes(
                Map.of(
                    "contractDocumentTenants",
                    List.copyOf(tenants),
                    "documentTemplateBindingTenants",
                    List.copyOf(tenants),
                    "documentIssuanceTenants",
                    List.copyOf(tenants),
                    "resourceRestriction",
                    claims.path("resourceRestriction").isTextual()
                        ? claims.path("resourceRestriction").asText()
                        : ""))
            .build();
      } catch (Exception error) {
        return null;
      }
    }
  }

  @org.springframework.web.bind.annotation.RestController
  static class SyntheticCacheControl {
    private final SecurityContextPort context;
    private final java.util.concurrent.atomic.AtomicInteger invocations =
        new java.util.concurrent.atomic.AtomicInteger();

    SyntheticCacheControl(SecurityContextPort context) {
      this.context = context;
    }

    @org.springframework.web.bind.annotation.PostMapping("/synthetic/response-cache-control")
    Mono<java.util.Map<String, Integer>> count() {
      return context
          .currentPrincipal()
          .filter(p -> p.hasScope("documents.issue"))
          .switchIfEmpty(
              Mono.error(
                  new org.springframework.web.server.ResponseStatusException(
                      org.springframework.http.HttpStatus.FORBIDDEN, "Synthetic control denied")))
          .map(p -> java.util.Map.of("invocations", invocations.incrementAndGet()));
    }
  }

  static final class PolicyState {
    final Set<String> denied = java.util.concurrent.ConcurrentHashMap.newKeySet();
  }

  @org.springframework.web.bind.annotation.RestController
  static final class SyntheticPolicyControl {
    private final SecurityContextPort context;
    private final PolicyState state;

    SyntheticPolicyControl(SecurityContextPort context, PolicyState state) {
      this.context = context;
      this.state = state;
    }

    @org.springframework.web.bind.annotation.PostMapping("/synthetic/policy-deny/{contract}")
    Mono<Map<String, Boolean>> deny(
        @org.springframework.web.bind.annotation.PathVariable UUID contract) {
      return context
          .currentPrincipal()
          .filter(p -> p.hasScope("contracts.admin"))
          .switchIfEmpty(
              Mono.error(
                  new org.springframework.web.server.ResponseStatusException(
                      org.springframework.http.HttpStatus.FORBIDDEN, "Synthetic control denied")))
          .map(
              p -> {
                state.denied.add(contract.toString());
                return Map.of("denied", true);
              });
    }
  }
}
