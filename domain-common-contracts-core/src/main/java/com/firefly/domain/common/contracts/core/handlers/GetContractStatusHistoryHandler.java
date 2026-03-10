package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.domain.common.contracts.core.queries.GetContractStatusHistoryQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Handles {@link GetContractStatusHistoryQuery} by retrieving the full status history of a contract.
 */
@QueryHandlerComponent
public class GetContractStatusHistoryHandler extends QueryHandler<GetContractStatusHistoryQuery, List<Object>> {

    private final ContractStatusHistoryApi contractStatusHistoryApi;

    public GetContractStatusHistoryHandler(ContractStatusHistoryApi contractStatusHistoryApi) {
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractStatusHistoryQuery query) {
        return contractStatusHistoryApi.filterContractStatusHistory(query.getContractId(), null)
                .mapNotNull(response -> response.getContent() != null
                        ? response.getContent()
                        : List.of());
    }
}
