package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve all terms associated with a specific contract.
 */
@Data
public class GetContractTermsQuery implements Query<List<Object>> {

    private UUID contractId;

    public GetContractTermsQuery(UUID contractId) {
        this.contractId = contractId;
    }
}
