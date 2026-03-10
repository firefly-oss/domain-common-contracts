package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

/**
 * Query to retrieve the current status of a specific contract.
 */
@Data
public class GetContractStatusQuery implements Query<Object> {

    private UUID contractId;

    public GetContractStatusQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
