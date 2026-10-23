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

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GovernedContractDocumentInputsTest {
  final String action = UUID.randomUUID().toString(),
      binding = UUID.randomUUID().toString(),
      revision = UUID.randomUUID().toString(),
      type = UUID.randomUUID().toString();

  String valid() {
    return "{\"actionId\":\""
        + action
        + "\",\"expectedContractRevision\":9007199254740993,\"selection\":{\"documentPurpose\":\"CONTRACT\",\"productScope\":\"NONE\",\"channelCode\":\"WEB\",\"jurisdiction\":\"US\",\"localeCode\":\"en-US\",\"businessAtEpochMillis\":-315619200000},\"expected\":{\"bindingId\":\""
        + binding
        + "\",\"bindingStateVersion\":1,\"revisionId\":\""
        + revision
        + "\",\"sourceDigest\":\""
        + "a".repeat(64)
        + "\"},\"contractDocumentTypeId\":\""
        + type
        + "\",\"productVersionReferences\":[],\"retention\":{\"policyCode\":\"CONTRACT_RECORD\",\"policyVersion\":\"1\",\"retainUntilEpochMillis\":253402300799999},\"displayName\":\"Synthetic"
        + " García contract\"}";
  }

  void rejects(String body) {
    assertThatThrownBy(() -> GovernedContractDocumentInputs.read(body))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(400);
              assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_INTENT_INVALID");
            });
  }

  @Test
  void exactIntegerPinsUnicodeAndSupportedPreEpochDatesRetainTheirMeaning() {
    var value = GovernedContractDocumentInputs.read(valid());
    assertThat(value.getActionId()).isEqualTo(action);
    assertThat(value.getExpectedContractRevision()).isEqualTo(9007199254740993L);
    assertThat(value.getDisplayName()).isEqualTo("Synthetic García contract");
    assertThat(value.getSelection().getBusinessAtEpochMillis()).isEqualTo(-315619200000L);
    assertThat(value.getProductVersionReferences()).isEmpty();
  }

  @Test
  void scalarCoercionDuplicatesTrailingAndAuthorityFieldsReject() {
    for (String body :
        List.of(
            valid().replace("9007199254740993", "7.5"),
            valid().replace("9007199254740993", "\"7\""),
            valid().replace("\"actionId\":\"" + action + "\"", "\"actionId\":123"),
            valid() + " {}",
            valid()
                .replace(
                    "\"expectedContractRevision\":9007199254740993",
                    "\"expectedContractRevision\":7,\"expectedContractRevision\":8"),
            valid()
                .replace(
                    "{\"actionId\":", "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"actionId\":"),
            valid().replace("{\"actionId\":", "{\"factsJson\":\"{}\",\"actionId\":")))
      rejects(body);
  }

  @Test
  void completeExactShapeAndCanonicalIdentitiesAreMandatory() {
    rejects(
        valid()
            .replace(
                "\"retention\":{\"policyCode\":\"CONTRACT_RECORD\",\"policyVersion\":\"1\",\"retainUntilEpochMillis\":253402300799999},",
                ""));
    rejects(valid().replace(action, "1-1-1-1-1"));
    rejects(valid().replace("\"NONE\"", "\"EXACT\""));
    rejects(
        valid()
            .replace(
                "\"productScope\":\"NONE\"",
                "\"productScope\":\"NONE\",\"productId\":\"" + UUID.randomUUID() + "\""));
    rejects(
        valid().replace("\"sourceDigest\":\"" + "a".repeat(64) + "\"", "\"sourceDigest\":\"bad\""));
  }

  @Test
  void invalidEnumsEpochOverflowAndBoundedBodyReject() {
    rejects(valid().replace("\"NONE\"", "\"LATEST\""));
    rejects(valid().replace("253402300799999", "253402300800000"));
    rejects(valid().replace("9007199254740993", "9223372036854775808"));
    rejects(" ");
    rejects(valid().replace("Synthetic García contract", "x".repeat(70000)));
  }
}
