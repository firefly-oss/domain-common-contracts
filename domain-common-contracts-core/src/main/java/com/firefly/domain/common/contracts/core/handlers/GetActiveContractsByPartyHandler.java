package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.domain.common.contracts.core.queries.GetActiveContractsByPartyQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles {@link GetActiveContractsByPartyQuery} by retrieving only active contracts for a party.
 */
@QueryHandlerComponent
public class GetActiveContractsByPartyHandler extends QueryHandler<GetActiveContractsByPartyQuery, List<Object>> {

    private final GlobalContractPartiesApi globalContractPartiesApi;

    public GetActiveContractsByPartyHandler(GlobalContractPartiesApi globalContractPartiesApi) {
        this.globalContractPartiesApi = globalContractPartiesApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetActiveContractsByPartyQuery query) {
        return globalContractPartiesApi.getContractPartiesByPartyId(query.getPartyId(), true, UUID.randomUUID().toString())
                .mapNotNull(response -> response.getContent() != null
                        ? new ArrayList<>(response.getContent())
                        : List.of());
    }
}
