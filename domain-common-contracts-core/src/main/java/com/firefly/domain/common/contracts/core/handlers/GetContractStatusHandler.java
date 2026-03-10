package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.domain.common.contracts.core.queries.GetContractStatusQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link GetContractStatusQuery} by retrieving the latest status history entry
 * for a contract, representing the current status.
 */
@QueryHandlerComponent
public class GetContractStatusHandler extends QueryHandler<GetContractStatusQuery, Object> {

    private final ContractStatusHistoryApi contractStatusHistoryApi;

    public GetContractStatusHandler(ContractStatusHistoryApi contractStatusHistoryApi) {
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    @Override
    protected Mono<Object> doHandle(GetContractStatusQuery query) {
        return contractStatusHistoryApi.filterContractStatusHistory(query.getContractId(), null)
                .mapNotNull(response -> {
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        return response.getContent().getFirst();
                    }
                    return null;
                });
    }
}
