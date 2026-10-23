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

import io.fireflysoftware.core.contract.sdk.model.ContractDocumentEnrichment;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentFactPreparation;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Captured source identities are the complete permitted set, never an invitation to discover more.
 */
@FunctionalInterface
public interface ContractDocumentEnrichmentCollector {
  Mono<ContractDocumentEnrichment> collect(
      UUID tenant, UUID contract, UUID action, ContractDocumentFactPreparation captured);
}
