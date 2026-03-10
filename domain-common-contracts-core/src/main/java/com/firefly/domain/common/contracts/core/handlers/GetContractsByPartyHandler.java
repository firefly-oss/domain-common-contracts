package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.domain.common.contracts.core.queries.GetContractsByPartyQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@link GetContractsByPartyQuery} by retrieving all contracts for a given party.
 */
@QueryHandlerComponent
public class GetContractsByPartyHandler extends QueryHandler<GetContractsByPartyQuery, List<Object>> {

    private final GlobalContractPartiesApi globalContractPartiesApi;

    public GetContractsByPartyHandler(GlobalContractPartiesApi globalContractPartiesApi) {
        this.globalContractPartiesApi = globalContractPartiesApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractsByPartyQuery query) {
        return globalContractPartiesApi.getContractPartiesByPartyId(query.getPartyId(), null)
                .mapNotNull(response -> response.getContent() != null
                        ? new ArrayList<>(response.getContent())
                        : List.of());
    }
}
