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

import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequestResult;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
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
