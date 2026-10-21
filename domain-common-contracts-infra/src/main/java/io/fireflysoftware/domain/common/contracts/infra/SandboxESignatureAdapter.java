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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministic sample e-signature provider used when no external provider is configured.
 * The same contract, document, and signer always receive the same signature request id.
 */
@Component
@ConditionalOnProperty(name = "integration.esignature.provider", havingValue = "sandbox", matchIfMissing = true)
@Slf4j
public class SandboxESignatureAdapter implements ESignaturePort {

    static final String PROVIDER = "SANDBOX";

    @Override
    public Mono<SignatureRequestResult> requestSignature(SignatureRequest request) {
        String signatureRequestId = UUID.nameUUIDFromBytes(sampleKey(request).getBytes(StandardCharsets.UTF_8)).toString();
        log.debug("Sandbox e-signature request for contract: {}", request.getContractId());
        return Mono.just(SignatureRequestResult.builder()
                .signatureRequestId(signatureRequestId)
                .provider(PROVIDER)
                .status("REQUESTED")
                .build());
    }

    @Override
    public Mono<SignatureVerificationResult> verifySignature(String signatureRequestId) {
        log.debug("Sandbox e-signature verification for request: {}", signatureRequestId);
        return Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(signatureRequestId)
                .status("VERIFIED")
                .provider(PROVIDER)
                .build());
    }

    private static String sampleKey(SignatureRequest request) {
        return "signature:"
                + request.getContractId()
                + ":"
                + request.getDocumentId()
                + ":"
                + request.getSignerPartyId();
    }
}
