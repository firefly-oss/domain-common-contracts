package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.domain.common.contracts.core.queries.GetContractTermsQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Handles {@link GetContractTermsQuery} by retrieving all terms of a contract.
 */
@QueryHandlerComponent
public class GetContractTermsHandler extends QueryHandler<GetContractTermsQuery, List<Object>> {

    private final ContractTermsApi contractTermsApi;

    public GetContractTermsHandler(ContractTermsApi contractTermsApi) {
        this.contractTermsApi = contractTermsApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractTermsQuery query) {
        return contractTermsApi.filterContractTerms(query.getContractId(), null)
                .mapNotNull(response -> response.getContent() != null
                        ? response.getContent()
                        : List.of());
    }
}
