package com.firefly.domain.common.contracts.infra.ports.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the result of verifying an e-signature request.
 */
@Data
@Builder
public class SignatureVerificationResult {
    private String signatureRequestId;
    private String status;
    private String provider;
}
