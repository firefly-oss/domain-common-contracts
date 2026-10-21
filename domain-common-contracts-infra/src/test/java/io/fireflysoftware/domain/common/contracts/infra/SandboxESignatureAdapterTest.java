/*
 * Copyright 2025 Firefly Software Foundation
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


package io.fireflysoftware.domain.common.contracts.infra;

import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxESignatureAdapterTest {

    private SandboxESignatureAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SandboxESignatureAdapter();
    }

    @Test
    void requestSignature_returnsTheSameIdForTheSameParties() {
        UUID contractId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID documentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID signerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        SignatureRequest request = SignatureRequest.builder()
                .contractId(contractId)
                .documentId(documentId)
                .signerPartyId(signerId)
                .build();

        StepVerifier.create(adapter.requestSignature(request).zipWith(adapter.requestSignature(request)))
                .assertNext(pair -> {
                    assertThat(pair.getT1().getProvider()).isEqualTo("SANDBOX");
                    assertThat(pair.getT1().getStatus()).isEqualTo("REQUESTED");
                    assertThat(pair.getT1().getSignatureRequestId()).isEqualTo(pair.getT2().getSignatureRequestId());
                })
                .verifyComplete();
    }

    @Test
    void verifySignature_returnsVerifiedForTheRequestId() {
        String requestId = "sample-request";

        StepVerifier.create(adapter.verifySignature(requestId))
                .assertNext(result -> {
                    assertThat(result.getProvider()).isEqualTo("SANDBOX");
                    assertThat(result.getStatus()).isEqualTo("VERIFIED");
                    assertThat(result.getSignatureRequestId()).isEqualTo(requestId);
                })
                .verifyComplete();
    }
}
