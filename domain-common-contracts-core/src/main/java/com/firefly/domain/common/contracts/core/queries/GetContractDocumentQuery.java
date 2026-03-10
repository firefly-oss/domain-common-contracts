package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

/**
 * Query to retrieve a specific document from a contract.
 */
@Data
public class GetContractDocumentQuery implements Query<Object> {

    private UUID contractId;
    private UUID documentId;

    public GetContractDocumentQuery(UUID contractId, UUID documentId) {
        this.contractId = contractId;
        this.documentId = documentId;
    }
}
