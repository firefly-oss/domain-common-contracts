package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve the full status history of a specific contract.
 */
@Data
public class GetContractStatusHistoryQuery implements Query<List<Object>> {

    private UUID contractId;

    public GetContractStatusHistoryQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
