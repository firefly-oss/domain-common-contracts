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

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GovernedDocumentAccessGuardTest {
  final UUID tenant = UUID.randomUUID(), contract = UUID.randomUUID(), action = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");
  final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

  SecurityPrincipal principal() {
    return SecurityPrincipal.builder()
        .subject("synthetic-owner")
        .issuer("synthetic-issuer")
        .tenantId(tenant.toString())
        .claims(Map.of("exp", now.plusSeconds(30)))
        .build();
  }

  GovernedDocumentAccessGuard guard(SecurityPrincipal principal, PolicyDecisionPort policy) {
    var beans = new StaticListableBeanFactory();
    if (principal != null)
      beans.addBean("context", (SecurityContextPort) () -> Mono.just(principal));
    if (policy != null) beans.addBean("policy", policy);
    return new GovernedDocumentAccessGuard(
        beans.getBeanProvider(SecurityContextPort.class),
        beans.getBeanProvider(PolicyDecisionPort.class),
        clock,
        true);
  }

  void denied(GovernedDocumentAccessGuard guard) {
    assertThatThrownBy(() -> guard.authorize(tenant, contract, action, "issue", Map.of()).block())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(403);
              assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_ACCESS_DENIED");
            });
  }

  @Test
  void missingTrustedContextOrPolicyDenies() {
    denied(guard(null, (p, a, r, c) -> Mono.just(Decision.permit())));
    denied(guard(principal(), null));
  }

  @Test
  void exactPolicyContextUsesContractActionAndSafeSelectionOnly() {
    AtomicInteger calls = new AtomicInteger();
    var p = principal();
    var g =
        guard(
            p,
            (seen, verb, resource, context) -> {
              calls.incrementAndGet();
              assertThat(seen).isSameAs(p);
              assertThat(verb).isEqualTo("contracts.document.issue");
              assertThat(resource).isEqualTo("tenant/" + tenant + "/CONTRACT/" + contract);
              assertThat(context)
                  .containsEntry("tenantId", tenant.toString())
                  .containsEntry("actionId", action.toString())
                  .containsEntry("resourceId", contract.toString())
                  .containsEntry("documentPurpose", "CONTRACT")
                  .doesNotContainKeys("factsJson", "bearer", "displayName");
              assertThatThrownBy(() -> context.put("mutate", "bad"))
                  .isInstanceOf(UnsupportedOperationException.class);
              return Mono.just(Decision.permit());
            });
    assertThat(
            g.authorize(
                    tenant,
                    contract,
                    action,
                    "issue",
                    Map.of(
                        "documentPurpose",
                        "CONTRACT",
                        "factsJson",
                        "private",
                        "bearer",
                        "secret",
                        "displayName",
                        "private title"))
                .block())
        .isSameAs(p);
    assertThat(calls).hasValue(1);
  }

  @Test
  void currentPolicyIsReevaluatedForWarmReplay() {
    AtomicBoolean permit = new AtomicBoolean(true);
    AtomicInteger calls = new AtomicInteger();
    var g =
        guard(
            principal(),
            (p, a, r, c) -> {
              calls.incrementAndGet();
              return Mono.just(
                  permit.get() ? Decision.permit() : Decision.deny("private policy detail"));
            });
    assertThat(g.authorize(tenant, contract, action, "issue", Map.of()).block()).isNotNull();
    permit.set(false);
    denied(g);
    assertThat(calls).hasValue(2);
  }

  @Test
  void anotherTenantDoesNotReachPolicy() {
    AtomicInteger calls = new AtomicInteger();
    denied(
        guard(
            principal().toBuilder().tenantId(UUID.randomUUID().toString()).build(),
            (p, a, r, c) -> {
              calls.incrementAndGet();
              return Mono.just(Decision.permit());
            }));
    assertThat(calls).hasValue(0);
  }

  @Test
  void missingExpiredOrCoercedExpiryDeniesBeforePolicy() {
    for (var claims :
        List.<Map<String, Object>>of(
            Map.of(),
            Map.of("exp", now),
            Map.of("exp", now.minusSeconds(1)),
            Map.of("exp", now.plusSeconds(30).toString()),
            Map.of("exp", now.plusSeconds(30).getEpochSecond()))) {
      AtomicInteger calls = new AtomicInteger();
      denied(
          guard(
              principal().toBuilder().claims(claims).build(),
              (p, a, r, c) -> {
                calls.incrementAndGet();
                return Mono.just(Decision.permit());
              }));
      assertThat(calls).hasValue(0);
    }
  }

  @Test
  void policyErrorsAreSanitized() {
    denied(
        guard(
            principal(),
            (p, a, r, c) ->
                Mono.error(new IllegalStateException("sensitive raw dependency detail"))));
  }

  @Test
  void unsignedBodyTenantCannotCreateDelegation() {
    denied(
        guard(
            principal().toBuilder()
                .tenantId("other")
                .attributes(Map.of("contractDocumentTenants", List.of(tenant.toString())))
                .build(),
            (p, a, r, c) -> Mono.just(Decision.permit())));
  }

  @Test
  void validatedDelegatedTenantStillRequiresExactPolicy() {
    var delegated =
        principal().toBuilder()
            .tenantId("service")
            .authorities(Set.of("contracts.document.delegate"))
            .attributes(Map.of("contractDocumentTenants", List.of(tenant.toString())))
            .build();
    assertThat(
            guard(delegated, (p, a, r, c) -> Mono.just(Decision.permit()))
                .authorize(tenant, contract, action, "read", Map.of())
                .block())
        .isSameAs(delegated);
  }

  @Test
  void unknownOperationOrMissingActionFailsClosed() {
    var g = guard(principal(), (p, a, r, c) -> Mono.just(Decision.permit()));
    assertThatThrownBy(
            () -> g.authorize(tenant, contract, action, "delete-evidence", Map.of()).block())
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> g.authorize(tenant, contract, null, "issue", Map.of()).block())
        .isInstanceOf(ResponseStatusException.class);
  }
}
