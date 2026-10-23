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

import io.fireflysoftware.domain.common.contracts.core.documents.v1.*;
import io.fireflysoftware.domain.common.contracts.infra.documents.v1.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runtime composition belongs to the web application; the core remains independently testable. */
@Configuration(proxyBeanMethods = false)
public class GovernedContractDocumentConfiguration {
  @Bean
  ContractDocumentEnrichmentCollector contractDocumentEnrichmentCollector(
      ContractDocumentSourcePort sources) {
    return new CapturedContractEnrichmentCollector(sources);
  }

  @Bean
  GovernedContractDocumentCoordinator governedContractDocumentCoordinator(
      GovernedDocumentAccessGuard guard,
      GovernedDocumentPeerPort peers,
      ContractDocumentEnrichmentCollector collector) {
    return new GovernedContractDocumentCoordinator(guard, peers, collector);
  }
}
