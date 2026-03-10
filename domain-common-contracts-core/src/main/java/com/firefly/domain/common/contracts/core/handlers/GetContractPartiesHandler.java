package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.domain.common.contracts.core.queries.GetContractPartiesQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Handles {@link GetContractPartiesQuery} by retrieving all parties of a contract.
 */
@QueryHandlerComponent
public class GetContractPartiesHandler extends QueryHandler<GetContractPartiesQuery, List<Object>> {

    private final ContractPartiesApi contractPartiesApi;

    public GetContractPartiesHandler(ContractPartiesApi contractPartiesApi) {
        this.contractPartiesApi = contractPartiesApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractPartiesQuery query) {
        return contractPartiesApi.filterContractParties(query.getContractId(), null)
                .mapNotNull(response -> response.getContent() != null
                        ? response.getContent()
                        : List.of());
    }
}
