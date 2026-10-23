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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.commons.ecm.sdk.model.*;
import io.fireflysoftware.core.contract.sdk.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SealedContractDocumentRequestTest {
  final UUID tenant = UUID.randomUUID(), contract = UUID.randomUUID(), action = UUID.randomUUID();
  final String facts =
      "{\"name\":\"Synthetic García"
          + " \uD83D\uDE00\",\"large\":9007199254740993,\"decimal\":123456.780000000000000001,\"opaque\":1e-128}";

  String hash(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  ContractDocumentFactPreparation preparation(String code) throws Exception {
    var request =
        new DocumentIssueRequest()
            .actionId(action.toString())
            .factsJson(facts)
            .definition(
                new DocumentIssueDefinition()
                    .factsContract(new DocumentFactsContract().code(code).version("1")));
    String exact = new ObjectMapper().writeValueAsString(request);
    var operation =
        new ContractDocumentOperation()
            .operationId(UUID.randomUUID().toString())
            .tenantId(tenant.toString())
            .contractId(contract.toString())
            .actionId(action.toString())
            .phase(ContractDocumentOperation.PhaseEnum.READY)
            .factsDigest(hash(facts))
            .sealedRequestDigest(hash(exact));
    return new ContractDocumentFactPreparation()
        .operation(operation)
        .sealedFactsJson(facts)
        .sealedIssueRequestJson(exact);
  }

  void invalid(ContractDocumentOperation expected, ContractDocumentFactPreparation response) {
    assertThatThrownBy(() -> SealedContractDocumentRequest.verify(expected, response))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(502);
              assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_PEER_INVALID");
            });
  }

  @Test
  void retainedFactsRemainByteExactWithUnicodeLargeIntegerAnd128Scale() throws Exception {
    var response = preparation("CONTRACT_DOCUMENT_V1");
    var decoded = SealedContractDocumentRequest.verify(response.getOperation(), response);
    assertThat(decoded.getActionId()).isEqualTo(action.toString());
    assertThat(decoded.getFactsJson()).isEqualTo(facts);
  }

  @Test
  void factsAndRetainedRequestDigestsMustBothMatch() throws Exception {
    var response = preparation("CONTRACT_DOCUMENT_V1");
    response.setSealedFactsJson(facts + " ");
    invalid(response.getOperation(), response);
    response = preparation("CONTRACT_DOCUMENT_V1");
    response.setSealedIssueRequestJson(response.getSealedIssueRequestJson() + " ");
    invalid(response.getOperation(), response);
  }

  @Test
  void stationeryFactsContractsCannotMasqueradeAsContractDocumentV1() throws Exception {
    var response = preparation("LOAN_AGREEMENT_V1");
    invalid(response.getOperation(), response);
  }

  @Test
  void partialOrDifferentOperationResponseIsNotAccepted() throws Exception {
    var response = preparation("CONTRACT_DOCUMENT_V1");
    var expected = response.getOperation();
    invalid(expected, new ContractDocumentFactPreparation());
    var other = preparation("CONTRACT_DOCUMENT_V1");
    other.getOperation().setTenantId(UUID.randomUUID().toString());
    invalid(expected, other);
    other = preparation("CONTRACT_DOCUMENT_V1");
    other.getOperation().setActionId(UUID.randomUUID().toString());
    invalid(expected, other);
  }

  @Test
  void duplicatedOrTrailingRequestContentRejectsEvenWithMatchingDigest() throws Exception {
    for (String suffix : new String[] {" {}", ",\"actionId\":\"" + action + "\"}"}) {
      var response = preparation("CONTRACT_DOCUMENT_V1");
      String body =
          suffix.startsWith(",")
              ? response
                      .getSealedIssueRequestJson()
                      .substring(0, response.getSealedIssueRequestJson().length() - 1)
                  + suffix
              : response.getSealedIssueRequestJson() + suffix;
      response.setSealedIssueRequestJson(body);
      response.getOperation().setSealedRequestDigest(hash(body));
      invalid(response.getOperation(), response);
    }
  }

  @Test
  void factsInRequestMustEqualRetainedFacts() throws Exception {
    var response = preparation("CONTRACT_DOCUMENT_V1");
    String body =
        response.getSealedIssueRequestJson().replace("9007199254740993", "9007199254740992");
    response.setSealedIssueRequestJson(body);
    response.getOperation().setSealedRequestDigest(hash(body));
    invalid(response.getOperation(), response);
  }
}
