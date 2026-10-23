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

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Projects only the secured route's signature-validated JWT, retaining its transient credential.
 */
final class GovernedDocumentAuthentication {
  private final PrincipalFactory factory;
  private final Clock clock;

  GovernedDocumentAuthentication(PrincipalFactory factory, Clock clock) {
    this.factory = Objects.requireNonNull(factory);
    this.clock = Objects.requireNonNull(clock);
  }

  Mono<JwtAuthenticationToken> convert(Jwt jwt) {
    return Mono.fromCallable(
            () -> {
              if (jwt == null
                  || jwt.getIssuer() == null
                  || !text(jwt.getSubject(), 255)
                  || jwt.getTokenValue() == null
                  || jwt.getTokenValue().length() > 16384
                  || jwt.getExpiresAt() == null
                  || !jwt.getExpiresAt().isAfter(clock.instant())
                  || jwt.getExpiresAt().isAfter(Instant.parse("9999-12-31T23:59:59Z")))
                throw invalid();
              var principal =
                  factory.fromClaims(jwt.getSubject(), jwt.getIssuer().toString(), jwt.getClaims());
              String tenant = null;
              Object tenantClaim = jwt.getClaim("tenant_id");
              if (tenantClaim != null) {
                if (!(tenantClaim instanceof String value)) throw invalid();
                tenant = uuid(value);
              }
              var attributes = new LinkedHashMap<String, Object>();
              Object delegated = jwt.getClaim("contract_document_tenants");
              if (delegated != null) {
                if (!(delegated instanceof List<?> values)
                    || values.isEmpty()
                    || values.size() > 100) throw invalid();
                var tenants = new ArrayList<String>();
                var unique = new HashSet<String>();
                for (Object value : values) {
                  if (!(value instanceof String item) || !unique.add(uuid(item))) throw invalid();
                  tenants.add(item);
                }
                attributes.put("contractDocumentTenants", List.copyOf(tenants));
              }
              if (tenant == null
                  && (!principal.hasAuthority("contracts.document.delegate")
                      || attributes.isEmpty())) throw invalid();
              principal = principal.toBuilder().tenantId(tenant).attributes(attributes).build();
              var authorities =
                  principal.authorities().stream().map(SimpleGrantedAuthority::new).toList();
              return new JwtAuthenticationToken(jwt, principal, authorities);
            })
        .onErrorMap(error -> invalid());
  }

  private static String uuid(String value) {
    var id = UUID.fromString(value);
    if (!id.toString().equals(value)) throw invalid();
    return value;
  }

  private static boolean text(String value, int limit) {
    return value != null
        && !value.isBlank()
        && value.length() <= limit
        && !value
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff || c == 0xfffd);
  }

  private static OAuth2AuthenticationException invalid() {
    return new OAuth2AuthenticationException(
        new OAuth2Error("invalid_token"), "Invalid governed document authentication");
  }
}
