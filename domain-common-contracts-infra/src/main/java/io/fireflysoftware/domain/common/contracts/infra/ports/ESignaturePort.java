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


package io.fireflysoftware.domain.common.contracts.infra.ports;

import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequestResult;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
import reactor.core.publisher.Mono;

/**
 * Port interface for e-signature operations.
 * Implementations provide the actual signature request and verification logic.
 */
public interface ESignaturePort {

    /**
     * Requests a signature for the given request details.
     *
     * @param request the signature request details
     * @return a {@link Mono} emitting the result of the signature request
     */
    Mono<SignatureRequestResult> requestSignature(SignatureRequest request);

    /**
     * Verifies the status of a previously submitted signature request.
     *
     * @param signatureRequestId the identifier of the signature request to verify
     * @return a {@link Mono} emitting the verification result
     */
    Mono<SignatureVerificationResult> verifySignature(String signatureRequestId);
}
