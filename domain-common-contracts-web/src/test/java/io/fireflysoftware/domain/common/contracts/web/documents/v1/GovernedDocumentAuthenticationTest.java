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

import java.time.*;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class GovernedDocumentAuthenticationTest {
  final UUID tenant = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T00:00:00Z");
  final GovernedDocumentAuthentication converter =
      new GovernedDocumentAuthentication(
          new PrincipalFactory(
              new ConfigurableAuthorityMapper(AuthorityMappingProperties.defaults())),
          Clock.fixed(now, ZoneOffset.UTC));

  Jwt.Builder token() {
    return Jwt.withTokenValue("synthetic.signed.token")
        .header("alg", "RS256")
        .issuer("https://synthetic.invalid/issuer")
        .subject("synthetic-owner")
        .audience(List.of("domain-contracts", "core-contracts"))
        .issuedAt(now.minusSeconds(10))
        .expiresAt(now.plusSeconds(60));
  }

  void denied(Jwt token) {
    assertThatThrownBy(() -> converter.convert(token).block())
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void standardJwtAuthenticationRetainsTokenAndJaflyPrincipalFromTheSameClaims() {
    var jwt =
        token()
            .claim("tenant_id", tenant.toString())
            .claim("scope", "contracts.document.issue contracts.document.read")
            .claim("roles", List.of("contracts.document.fact-producer"))
            .build();
    var authentication = converter.convert(jwt).block();
    assertThat(authentication.getToken()).isSameAs(jwt);
    assertThat(authentication.getPrincipal()).isInstanceOf(SecurityPrincipal.class);
    var principal = (SecurityPrincipal) authentication.getPrincipal();
    assertThat(principal.tenantId()).isEqualTo(tenant.toString());
    assertThat(principal.scopes())
        .containsExactlyInAnyOrder("contracts.document.issue", "contracts.document.read");
    assertThat(principal.hasAuthority("contracts.document.fact-producer")).isTrue();
    assertThat(principal.claims()).isEqualTo(jwt.getClaims());
    assertThat(
            new ReactorSecurityContextAdapter()
                .currentPrincipal()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block())
        .isSameAs(principal);
  }

  @Test
  void exactSignedDelegationCanRepresentAServiceWithoutATenant() {
    var jwt =
        token()
            .claim("roles", List.of("contracts.document.delegate"))
            .claim("contract_document_tenants", List.of(tenant.toString()))
            .build();
    var p = (SecurityPrincipal) converter.convert(jwt).block().getPrincipal();
    assertThat(p.tenantId()).isNull();
    assertThat(p.attributes()).containsEntry("contractDocumentTenants", List.of(tenant.toString()));
  }

  @Test
  void missingTenantAndDelegationDenies() {
    denied(token().build());
  }

  @Test
  void unprivilegedDelegationCannotReplaceTenant() {
    denied(token().claim("contract_document_tenants", List.of(tenant.toString())).build());
  }

  @Test
  void nonCanonicalOrCoercedTenantDenies() {
    denied(token().claim("tenant_id", tenant.toString().toUpperCase(Locale.ROOT)).build());
    denied(token().claim("tenant_id", 123).build());
  }

  @Test
  void duplicateMalformedOrUnboundedDelegationDenies() {
    denied(
        token()
            .claim("roles", List.of("contracts.document.delegate"))
            .claim("contract_document_tenants", List.of(tenant.toString(), tenant.toString()))
            .build());
    denied(
        token()
            .claim("roles", List.of("contracts.document.delegate"))
            .claim("contract_document_tenants", List.of("not-a-uuid"))
            .build());
    denied(
        token()
            .claim("roles", List.of("contracts.document.delegate"))
            .claim(
                "contract_document_tenants",
                java.util.stream.IntStream.range(0, 101)
                    .mapToObj(i -> UUID.randomUUID().toString())
                    .toList())
            .build());
  }

  @Test
  void missingExpiredOrUnusableTokenIdentityDenies() {
    denied(token().claim("tenant_id", tenant.toString()).expiresAt(now).build());
    denied(token().claim("tenant_id", tenant.toString()).subject(" ").build());
    denied(
        Jwt.withTokenValue("synthetic.signed.token")
            .header("alg", "RS256")
            .subject("synthetic-owner")
            .claim("tenant_id", tenant.toString())
            .build());
  }
}
