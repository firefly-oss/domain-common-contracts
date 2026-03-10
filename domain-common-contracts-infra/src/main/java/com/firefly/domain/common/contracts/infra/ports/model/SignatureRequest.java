package com.firefly.domain.common.contracts.infra.ports.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Represents a request to obtain an e-signature on a contract document.
 */
@Data
@Builder
public class SignatureRequest {
    private UUID contractId;
    private UUID documentId;
    private UUID signerPartyId;
}
