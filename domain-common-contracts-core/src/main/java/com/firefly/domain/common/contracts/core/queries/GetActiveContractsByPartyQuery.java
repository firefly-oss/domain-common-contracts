package com.firefly.domain.common.contracts.core.queries;

import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Query to retrieve all active contracts associated with a given party.
 */
@Data
public class GetActiveContractsByPartyQuery implements Query<List<Object>> {

    private UUID partyId;

    public GetActiveContractsByPartyQuery(UUID partyId) {
        this.partyId = partyId;
    }
}
