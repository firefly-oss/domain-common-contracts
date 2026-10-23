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

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Explicit relay of the current validated multi-audience authentication, never a stored grant. */
public final class GovernedDocumentRelay implements GovernedDocumentCredentialPort {
  private final String mode;
  private final Map<Peer, String> audiences;
  private final ObjectProvider<SecurityContextPort> contexts;
  private final Clock clock;

  public GovernedDocumentRelay(
      String mode,
      Map<Peer, String> audiences,
      ObjectProvider<SecurityContextPort> contexts,
      Clock clock) {
    this.mode = mode;
    this.audiences = Map.copyOf(audiences);
    this.contexts = Objects.requireNonNull(contexts);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<Credential> current(Peer peer, UUID tenant, UUID contract, UUID action) {
    return Mono.defer(
            () -> {
              if (!"validated-multi-audience".equals(mode)) return Mono.error(unavailable());
              String audience = audiences.get(peer);
              var context = contexts.getIfAvailable();
              if (context == null
                  || audience == null
                  || audience.isBlank()
                  || tenant == null
                  || contract == null
                  || action == null) return Mono.error(denied());
              return ReactiveSecurityContextHolder.getContext()
                  .map(security -> security.getAuthentication())
                  .filter(auth -> auth instanceof JwtAuthenticationToken && auth.isAuthenticated())
                  .cast(JwtAuthenticationToken.class)
                  .flatMap(
                      auth -> {
                        if (!(auth.getPrincipal() instanceof SecurityPrincipal principal))
                          return Mono.error(denied());
                        Jwt jwt = auth.getToken();
                        return context
                            .currentPrincipal()
                            .filter(
                                current ->
                                    principal.equals(current)
                                        && valid(principal, jwt, tenant, audience))
                            .map(
                                current -> new Credential(jwt.getTokenValue(), jwt.getExpiresAt()));
                      })
                  .switchIfEmpty(Mono.error(denied()));
            })
        .timeout(Duration.ofSeconds(5))
        .onErrorMap(
            error -> {
              if (error instanceof ResponseStatusException response
                  && response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)
                return unavailable();
              return denied();
            });
  }

  private boolean valid(SecurityPrincipal principal, Jwt jwt, UUID tenant, String audience) {
    return jwt.getIssuer() != null
        && Objects.equals(principal.issuer(), jwt.getIssuer().toString())
        && principal.subject() != null
        && !principal.subject().isBlank()
        && Objects.equals(principal.subject(), jwt.getSubject())
        && principal.claims().equals(jwt.getClaims())
        && jwt.getExpiresAt() != null
        && jwt.getExpiresAt().isAfter(clock.instant())
        && jwt.getAudience().contains(audience)
        && jwt.getTokenValue() != null
        && jwt.getTokenValue().length() <= 16384
        && (tenant.toString().equals(principal.tenantId())
                && tenant.toString().equals(jwt.getClaim("tenant_id"))
            || delegated(principal, jwt, tenant));
  }

  private static boolean delegated(SecurityPrincipal principal, Jwt jwt, UUID tenant) {
    if (!principal.hasAuthority("contracts.document.delegate")
        || !(jwt.getClaim("contract_document_tenants") instanceof List<?> signed)
        || signed.isEmpty()
        || signed.size() > 100
        || !signed.equals(principal.attributes().get("contractDocumentTenants"))) return false;
    var seen = new HashSet<String>();
    for (Object value : signed) {
      if (!(value instanceof String text)) return false;
      try {
        if (!UUID.fromString(text).toString().equals(text) || !seen.add(text)) return false;
      } catch (Exception error) {
        return false;
      }
    }
    return signed.contains(tenant.toString());
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "GOVERNED_DOCUMENT_DELEGATION_REQUIRED");
  }

  private static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, "GOVERNED_DOCUMENT_DELEGATION_DENIED");
  }
}
