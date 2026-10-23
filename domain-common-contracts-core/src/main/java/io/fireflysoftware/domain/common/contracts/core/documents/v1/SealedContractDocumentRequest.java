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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentIssueRequest;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentFactPreparation;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentOperation;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentPrepareRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies exact retained strings; never canonicalizes or reconstructs a sealed financial payload.
 */
final class SealedContractDocumentRequest {
  private static final int MAX_BYTES = 2097152;
  private static final JsonMapper JSON =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(64)
                          .maxStringLength(1048576)
                          .maxNumberLength(128)
                          .build())
                  .build())
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
          .enable(
              DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
              DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
              DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .withCoercionConfig(
              LogicalType.Textual,
              c ->
                  c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                      .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                      .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
          .build();

  private SealedContractDocumentRequest() {}

  static ContractDocumentPrepareRequest copyIntent(ContractDocumentPrepareRequest intent) {
    try {
      if (intent == null
          || intent.getProductVersionReferences() != null
              && intent.getProductVersionReferences().size() > 100) throw invalid();
      String value = JSON.writeValueAsString(intent);
      bounded(value);
      return JSON.readValue(value, ContractDocumentPrepareRequest.class);
    } catch (Exception error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GOVERNED_DOCUMENT_INTENT_INVALID");
    }
  }

  static DocumentIssueRequest verify(
      ContractDocumentOperation expected, ContractDocumentFactPreparation preparation) {
    try {
      if (expected == null || preparation == null || preparation.getOperation() == null)
        throw invalid();
      var actual = preparation.getOperation();
      for (String id :
          List.of(
              expected.getOperationId(),
              expected.getTenantId(),
              expected.getContractId(),
              expected.getActionId()))
        if (!UUID.fromString(id).toString().equals(id)) throw invalid();
      if (expected.getPhase() != ContractDocumentOperation.PhaseEnum.READY
          || actual.getPhase() != expected.getPhase()
          || !Objects.equals(expected.getOperationId(), actual.getOperationId())
          || !Objects.equals(expected.getTenantId(), actual.getTenantId())
          || !Objects.equals(expected.getContractId(), actual.getContractId())
          || !Objects.equals(expected.getActionId(), actual.getActionId())
          || !Objects.equals(expected.getIntent(), actual.getIntent())
          || !Objects.equals(expected.getSourceRevision(), actual.getSourceRevision())
          || !Objects.equals(expected.getCoreSnapshotDigest(), actual.getCoreSnapshotDigest())
          || !Objects.equals(expected.getFactsDigest(), actual.getFactsDigest())
          || !Objects.equals(expected.getSealedRequestDigest(), actual.getSealedRequestDigest()))
        throw invalid();
      String facts = preparation.getSealedFactsJson(),
          request = preparation.getSealedIssueRequestJson();
      bounded(facts);
      bounded(request);
      if (!digest(facts).equals(expected.getFactsDigest())
          || !digest(request).equals(expected.getSealedRequestDigest())) throw invalid();
      var tree = JSON.readTree(facts);
      if (tree == null || !tree.isObject()) throw invalid();
      validateText(tree);
      var decoded = JSON.readValue(request, DocumentIssueRequest.class);
      if (decoded == null
          || !expected.getActionId().equals(decoded.getActionId())
          || !facts.equals(decoded.getFactsJson())
          || decoded.getDefinition() == null
          || decoded.getDefinition().getFactsContract() == null
          || !"CONTRACT_DOCUMENT_V1".equals(decoded.getDefinition().getFactsContract().getCode())
          || !"1".equals(decoded.getDefinition().getFactsContract().getVersion())) throw invalid();
      return decoded;
    } catch (Exception error) {
      throw invalid();
    }
  }

  static JsonNode snapshot(String value) {
    try {
      bounded(value);
      var node = JSON.readTree(value);
      if (node == null || !node.isObject()) throw invalid();
      validateText(node);
      return node;
    } catch (Exception error) {
      throw invalid();
    }
  }

  static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw invalid();
    }
  }

  private static void bounded(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_BYTES) throw invalid();
    text(value);
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid();
  }

  private static void validateText(JsonNode node) {
    if (node.isTextual()) text(node.textValue());
    if (node.isObject()) node.fieldNames().forEachRemaining(SealedContractDocumentRequest::text);
    if (node.isContainerNode()) node.forEach(SealedContractDocumentRequest::validateText);
  }

  private static void text(String value) {
    if (value.codePoints().anyMatch(c -> c >= 0xd800 && c <= 0xdfff || c == 0xfffd))
      throw invalid();
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_PEER_INVALID");
  }
}
