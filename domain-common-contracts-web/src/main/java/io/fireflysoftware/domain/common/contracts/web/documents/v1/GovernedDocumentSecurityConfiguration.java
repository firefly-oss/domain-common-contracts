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
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.AccessTokenValidators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

/** Only the new governed contract routes use this explicit issuer/audience trust boundary. */
@Configuration(proxyBeanMethods = false)
public class GovernedDocumentSecurityConfiguration {
  @Bean
  @Order(-100)
  SecurityWebFilterChain governedDocumentSecurity(
      ServerHttpSecurity http,
      PrincipalFactory factory,
      @Value("${firefly.contracts.documents.security.issuer:}") String issuer,
      @Value("${firefly.contracts.documents.security.audience:}") String audience,
      @Value("${firefly.contracts.documents.security.jwk-set-uri:}") String jwks) {
    ReactiveJwtDecoder decoder;
    if (issuer.isBlank() || audience.isBlank() || jwks.isBlank()) {
      decoder = token -> Mono.error(new JwtException("Governed document trust is not configured"));
    } else {
      var configured = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
      configured.setJwtValidator(AccessTokenValidators.forIssuerAndAudience(issuer, audience));
      decoder = configured;
    }
    var authentication = new GovernedDocumentAuthentication(factory, Clock.systemUTC());
    return http.securityMatcher(
            ServerWebExchangeMatchers.pathMatchers(
                "/api/v1/tenants/*/contracts/*/governed-documents",
                "/api/v1/tenants/*/contracts/*/governed-documents/**"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(e -> e.anyExchange().authenticated())
        .oauth2ResourceServer(
            resource ->
                resource.jwt(
                    jwt ->
                        jwt.jwtDecoder(decoder)
                            .jwtAuthenticationConverter(
                                token ->
                                    authentication
                                        .convert(token)
                                        .cast(AbstractAuthenticationToken.class))))
        .build();
  }

  /** Scoped application chains must precede this final denial boundary. */
  @Bean
  @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
  SecurityWebFilterChain governedDocumentUnmatchedDenial(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    new org.springframework.security.web.server.authentication
                        .HttpStatusServerEntryPoint(org.springframework.http.HttpStatus.FORBIDDEN)))
        .build();
  }
}
