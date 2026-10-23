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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Current authorization for the exact contract/action; decisions are never retained for replay. */
@Component
public class GovernedDocumentAccessGuard {
  private static final Set<String> OPERATIONS =
      Set.of("issue", "resume", "read", "collect-facts", "content.read");
  private static final Set<String> CONTEXT_KEYS =
      Set.of(
          "expectedContractRevision",
          "documentSourceRevision",
          "coreSnapshotDigest",
          "sealedRequestDigest",
          "factsDigest",
          "factsContractCode",
          "factsContractVersion",
          "contractDocumentTypeId",
          "documentPurpose",
          "productScope",
          "productId",
          "channelCode",
          "jurisdiction",
          "localeCode",
          "businessAtEpochMillis",
          "bindingId",
          "bindingStateVersion",
          "revisionId",
          "sourceDigest",
          "retentionPolicyCode",
          "retentionPolicyVersion",
          "retainUntilEpochMillis",
          "predecessorOperationId",
          "issuanceId",
          "documentId",
          "documentVersionId",
          "sourceSystem",
          "sourceType",
          "sourceId");
  private final ObjectProvider<SecurityContextPort> contexts;
  private final ObjectProvider<PolicyDecisionPort> policies;
  private final Clock clock;
  private final boolean strictPolicy;

  @Autowired
  public GovernedDocumentAccessGuard(
      ObjectProvider<SecurityContextPort> contexts,
      ObjectProvider<PolicyDecisionPort> policies,
      @Value("${firefly.security.resource-server.strict-policy:false}") boolean strictPolicy) {
    this(contexts, policies, Clock.systemUTC(), strictPolicy);
  }

  public GovernedDocumentAccessGuard(
      ObjectProvider<SecurityContextPort> contexts,
      ObjectProvider<PolicyDecisionPort> policies,
      Clock clock,
      boolean strictPolicy) {
    this.contexts = Objects.requireNonNull(contexts);
    this.policies = Objects.requireNonNull(policies);
    this.clock = Objects.requireNonNull(clock);
    this.strictPolicy = strictPolicy;
  }

  public Mono<SecurityPrincipal> authorize(
      UUID tenant, UUID contract, UUID action, String operation, Map<String, Object> context) {
    return Mono.defer(
            () -> {
              if (!strictPolicy) return Mono.error(denied());
              var security = contexts.getIfAvailable();
              var policy = policies.getIfAvailable();
              if (security == null
                  || policy == null
                  || tenant == null
                  || contract == null
                  || action == null
                  || !OPERATIONS.contains(operation)
                  || context == null) return Mono.error(denied());
              var values = new LinkedHashMap<String, Object>();
              context.forEach(
                  (key, value) -> {
                    if (CONTEXT_KEYS.contains(key) && safe(value)) values.put(key, value);
                  });
              values.put("tenantId", tenant.toString());
              values.put("resourceType", "CONTRACT");
              values.put("resourceId", contract.toString());
              values.put("actionId", action.toString());
              var immutable = Collections.unmodifiableMap(values);
              return security
                  .currentPrincipal()
                  .filter(
                      p ->
                          valid(p)
                              && (tenant.toString().equals(p.tenantId()) || delegated(p, tenant)))
                  .flatMap(
                      p ->
                          policy
                              .authorize(
                                  p,
                                  "contracts.document." + operation,
                                  "tenant/" + tenant + "/CONTRACT/" + contract,
                                  immutable)
                              .filter(d -> d.granted() && d.obligations().isEmpty())
                              .filter(d -> valid(p))
                              .map(d -> p))
                  .switchIfEmpty(Mono.error(denied()));
            })
        .timeout(Duration.ofSeconds(5))
        .onErrorMap(e -> denied());
  }

  private boolean valid(SecurityPrincipal principal) {
    return principal != null
        && text(principal.subject(), 255)
        && principal.claims().get("exp") instanceof Instant expiry
        && expiry.isAfter(clock.instant())
        && !expiry.isAfter(Instant.parse("9999-12-31T23:59:59Z"));
  }

  private static boolean delegated(SecurityPrincipal principal, UUID tenant) {
    return principal.hasAuthority("contracts.document.delegate")
        && principal.attributes().get("contractDocumentTenants") instanceof Collection<?> tenants
        && tenants.size() <= 100
        && tenants.contains(tenant.toString());
  }

  private static boolean safe(Object value) {
    return value instanceof String s && text(s, 500)
        || value instanceof Long
        || value instanceof Integer
        || value instanceof Boolean;
  }

  private static boolean text(String value, int limit) {
    return value != null
        && !value.isBlank()
        && value.length() <= limit
        && !value.codePoints().anyMatch(Character::isISOControl);
  }

  private static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, "GOVERNED_DOCUMENT_ACCESS_DENIED");
  }
}
