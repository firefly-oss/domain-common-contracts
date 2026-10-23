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
package io.fireflysoftware.domain.common.contracts.interfaces.dtos.document.v1;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Caller intent only; facts, tenant, resource and actor authority are absent by design. */
@Schema(
    description =
        "Immutable intent for CONTRACT_DOCUMENT_V1. Exact pins and product versions are required;"
            + " no implicit selection fallback.")
public record GovernedContractDocumentIntent(
    String actionId,
    Long expectedContractRevision,
    GovernedSelection selection,
    GovernedPins expected,
    String contractDocumentTypeId,
    List<GovernedProductVersion> productVersionReferences,
    GovernedRetention retention,
    String displayName,
    String predecessorOperationId) {
  public record GovernedSelection(
      String documentPurpose,
      GovernedProductScope productScope,
      String productId,
      String channelCode,
      String jurisdiction,
      String localeCode,
      Long businessAtEpochMillis) {}

  public enum GovernedProductScope {
    NONE,
    EXACT
  }

  public record GovernedPins(
      String bindingId, Long bindingStateVersion, String revisionId, String sourceDigest) {}

  public record GovernedProductVersion(String productId, String productVersionId) {}

  public record GovernedRetention(
      String policyCode, String policyVersion, Long retainUntilEpochMillis) {}
}
