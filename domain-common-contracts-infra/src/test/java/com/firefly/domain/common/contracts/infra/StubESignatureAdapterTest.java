package com.firefly.domain.common.contracts.infra;

import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
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
