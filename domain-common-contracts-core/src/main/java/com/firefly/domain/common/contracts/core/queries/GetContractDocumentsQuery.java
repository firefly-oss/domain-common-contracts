package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve all documents associated with a specific contract.
 */
@Data
public class GetContractDocumentsQuery implements Query<List<Object>> {

    private UUID contractId;

    public GetContractDocumentsQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
