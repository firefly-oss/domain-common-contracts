package com.firefly.domain.common.contracts.infra;

import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequestResult;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Stub implementation of {@link ESignaturePort} for development and testing.
 * Simulates e-signature operations with a brief delay and returns successful results.
 */
@Component
@ConditionalOnProperty(name = "integration.esignature.provider", havingValue = "stub", matchIfMissing = true)
@Slf4j
public class StubESignatureAdapter implements ESignaturePort {

    private static final String PROVIDER = "STUB";

    @Override
    public Mono<SignatureRequestResult> requestSignature(SignatureRequest request) {
        log.debug("Stub e-signature request for contract: {}", request.getContractId());
        return Mono.delay(Duration.ofMillis(100))
                .map(tick -> SignatureRequestResult.builder()
                        .signatureRequestId(UUID.randomUUID().toString())
                        .provider(PROVIDER)
                        .status("REQUESTED")
                        .build());
    }

    @Override
    public Mono<SignatureVerificationResult> verifySignature(String signatureRequestId) {
        log.debug("Stub e-signature verification for request: {}", signatureRequestId);
        return Mono.delay(Duration.ofMillis(100))
                .map(tick -> SignatureVerificationResult.builder()
                        .signatureRequestId(signatureRequestId)
                        .status("VERIFIED")
                        .provider(PROVIDER)
                        .build());
    }
}
