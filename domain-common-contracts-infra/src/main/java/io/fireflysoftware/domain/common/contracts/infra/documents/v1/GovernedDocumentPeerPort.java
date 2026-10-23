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

import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuanceReceipt;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssueRequest;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssuedContent;
import io.fireflysoftware.core.contract.sdk.model.*;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Generated-SDK transport to the existing native journal and immutable document issuer. */
public interface GovernedDocumentPeerPort {
  Mono<ContractDocumentOperation> prepare(
      UUID tenant, UUID contract, ContractDocumentPrepareRequest intent);

  Mono<ContractDocumentOperation> get(UUID tenant, UUID contract, UUID action);

  Mono<ContractDocumentFactPreparation> facts(UUID tenant, UUID contract, UUID action);

  Mono<ContractDocumentOperation> seal(
      UUID tenant, UUID contract, UUID action, ContractDocumentEnrichment enrichment);

  Mono<DocumentIssuanceReceipt> issue(
      UUID tenant, UUID contract, UUID action, DocumentIssueRequest retainedRequest);

  Mono<ContractDocumentOperation> record(UUID tenant, UUID contract, UUID action, UUID issuance);

  Mono<ContractDocumentOperation> attach(UUID tenant, UUID contract, UUID action);

  Mono<DocumentIssuedContent> content(UUID tenant, UUID contract, UUID action, UUID issuance);
}
