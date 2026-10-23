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

import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Trusted transient delegation only; callers cannot supply a bearer through the public request. */
@FunctionalInterface
public interface GovernedDocumentCredentialPort {
  enum Peer {
    CONTRACTS,
    DOCUMENTS,
    CUSTOMERS,
    PRODUCTS,
    MASTERS
  }

  Mono<Credential> current(Peer peer, UUID tenant, UUID contract, UUID action);

  final class Credential {
    private final String bearer;
    private final Instant expiresAt;

    public Credential(String bearer, Instant expiresAt) {
      this.bearer = bearer;
      this.expiresAt = expiresAt;
    }

    public String bearer() {
      return bearer;
    }

    public Instant expiresAt() {
      return expiresAt;
    }

    @Override
    public String toString() {
      return "GovernedDocumentCredential[redacted]";
    }
  }
}
