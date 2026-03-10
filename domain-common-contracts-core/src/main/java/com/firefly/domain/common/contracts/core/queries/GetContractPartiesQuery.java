package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve all parties associated with a specific contract.
 */
@Data
public class GetContractPartiesQuery implements Query<List<Object>> {

    private UUID contractId;

    public GetContractPartiesQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
