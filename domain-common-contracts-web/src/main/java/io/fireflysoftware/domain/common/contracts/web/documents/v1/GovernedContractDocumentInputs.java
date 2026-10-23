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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.fireflysoftware.core.contract.sdk.model.*;
import io.fireflysoftware.domain.common.contracts.interfaces.dtos.document.v1.GovernedContractDocumentIntent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strict route-local input decoding; no shared application mapper or opaque financial JSON changes.
 */
public final class GovernedContractDocumentInputs {
  private static final int MAX_BYTES = 65536;
  private static final JsonMapper JSON =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(16)
                          .maxStringLength(1024)
                          .maxNumberLength(20)
                          .build())
                  .build())
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
          .enable(
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
              DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
          .withCoercionConfig(
              LogicalType.Textual,
              c ->
                  c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                      .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                      .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
          .build();

  private GovernedContractDocumentInputs() {}

  public static ContractDocumentPrepareRequest read(String body) {
    try {
      if (body == null
          || body.isBlank()
          || body.length() > MAX_BYTES
          || body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid();
      var intent = JSON.readValue(body, GovernedContractDocumentIntent.class);
      if (intent == null
          || intent.expectedContractRevision() == null
          || intent.expectedContractRevision() < 0
          || intent.selection() == null
          || intent.expected() == null
          || intent.retention() == null) throw invalid();
      uuid(intent.actionId());
      uuid(intent.contractDocumentTypeId());
      key(intent.displayName(), 255);
      if (intent.predecessorOperationId() != null) uuid(intent.predecessorOperationId());
      var s = intent.selection();
      key(s.documentPurpose(), 100);
      key(s.channelCode(), 50);
      key(s.jurisdiction(), 50);
      key(s.localeCode(), 50);
      epoch(s.businessAtEpochMillis());
      if (s.productScope() == null
          || s.productScope() == GovernedContractDocumentIntent.GovernedProductScope.NONE
              && s.productId() != null
          || s.productScope() == GovernedContractDocumentIntent.GovernedProductScope.EXACT
              && s.productId() == null) throw invalid();
      if (s.productId() != null) uuid(s.productId());
      var pins = intent.expected();
      uuid(pins.bindingId());
      uuid(pins.revisionId());
      if (pins.bindingStateVersion() == null
          || pins.bindingStateVersion() < 1
          || pins.sourceDigest() == null
          || !pins.sourceDigest().matches("[a-f0-9]{64}")) throw invalid();
      var retention = intent.retention();
      if (!key(retention.policyCode(), 100).matches("[A-Z][A-Z0-9_]{0,99}")) throw invalid();
      key(retention.policyVersion(), 100);
      epoch(retention.retainUntilEpochMillis());
      if (intent.productVersionReferences() == null
          || intent.productVersionReferences().size() > 100) throw invalid();
      var products = new HashSet<String>();
      for (var ref : intent.productVersionReferences()) {
        if (ref == null) throw invalid();
        uuid(ref.productId());
        uuid(ref.productVersionId());
        if (!products.add(ref.productId())) throw invalid();
      }
      if (s.productScope() == GovernedContractDocumentIntent.GovernedProductScope.EXACT
          && !products.contains(s.productId())) throw invalid();
      return JSON.convertValue(intent, ContractDocumentPrepareRequest.class);
    } catch (Exception error) {
      throw invalid();
    }
  }

  private static void uuid(String value) {
    if (value == null
        || !value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
      throw invalid();
  }

  private static String key(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.length() > max
        || !value.equals(value.strip())
        || value
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff || c == 0xfffd))
      throw invalid();
    return value;
  }

  private static void epoch(Long value) {
    if (value == null || value < -2208988800000L || value > 253402300799999L) throw invalid();
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "GOVERNED_DOCUMENT_INTENT_INVALID");
  }
}
