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

package io.fireflysoftware.domain.common.contracts.core.documents;

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Compatibility boundary for callers that cannot express a governed document intent. */
@Service
public class KitDocumentService {
  public KitDocumentService(DocumentControllerApi documents) {}

  public Mono<UUID> issue(String templateCode, UUID partyId, UUID contractId) {
    return Mono.error(LegacyDocumentMigration.required());
  }

  public Mono<LegacyIssuedView> read(UUID documentId) {
    return Mono.error(LegacyDocumentMigration.required());
  }

  public record LegacyIssuedView(
      UUID id, String templateCode, String mediaType, String html, String name) {}
}
