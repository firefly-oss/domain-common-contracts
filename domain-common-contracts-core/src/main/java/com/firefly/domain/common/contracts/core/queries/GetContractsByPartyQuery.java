package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve all contracts associated with a given party.
 */
@Data
public class GetContractsByPartyQuery implements Query<List<Object>> {

    private UUID partyId;

    public GetContractsByPartyQuery(UUID partyId) {
        this.partyId = partyId;
    }
}
