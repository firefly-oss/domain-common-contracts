package com.firefly.domain.common.contracts.infra.ports;

import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequestResult;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
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
