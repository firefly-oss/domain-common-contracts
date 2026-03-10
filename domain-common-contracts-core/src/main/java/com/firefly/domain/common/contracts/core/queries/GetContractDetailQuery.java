package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

/**
 * Query to retrieve the details of a specific contract.
 */
@Data
public class GetContractDetailQuery implements Query<Object> {

    private UUID contractId;

    public GetContractDetailQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
