package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.domain.common.contracts.core.queries.GetContractDetailQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link GetContractDetailQuery} by retrieving a contract by its identifier.
 */
@QueryHandlerComponent
public class GetContractDetailHandler extends QueryHandler<GetContractDetailQuery, Object> {

    private final ContractsApi contractsApi;

    public GetContractDetailHandler(ContractsApi contractsApi) {
        this.contractsApi = contractsApi;
    }

    @Override
    protected Mono<Object> doHandle(GetContractDetailQuery query) {
        return contractsApi.getContractById(query.getContractId()).map(dto -> dto);
    }
}
