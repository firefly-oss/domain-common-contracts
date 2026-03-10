package com.firefly.domain.common.contracts.infra.ports.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the result of an e-signature request.
 */
@Data
@Builder
public class SignatureRequestResult {
    private String signatureRequestId;
    private String provider;
    private String status;
}
