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

class StubESignatureAdapterTest {

    private StubESignatureAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StubESignatureAdapter();
    }

    @Test
    void requestSignature_shouldReturnSuccessfulResult() {
        SignatureRequest request = SignatureRequest.builder()
                .contractId(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .signerPartyId(UUID.randomUUID())
                .build();

        StepVerifier.create(adapter.requestSignature(request))
                .assertNext(result -> {
                    assertThat(result.getProvider()).isEqualTo("STUB");
                    assertThat(result.getStatus()).isEqualTo("REQUESTED");
                    assertThat(result.getSignatureRequestId()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void verifySignature_shouldReturnVerifiedResult() {
        String requestId = UUID.randomUUID().toString();

        StepVerifier.create(adapter.verifySignature(requestId))
                .assertNext(result -> {
                    assertThat(result.getProvider()).isEqualTo("STUB");
                    assertThat(result.getStatus()).isEqualTo("VERIFIED");
                    assertThat(result.getSignatureRequestId()).isEqualTo(requestId);
                })
                .verifyComplete();
    }
}
