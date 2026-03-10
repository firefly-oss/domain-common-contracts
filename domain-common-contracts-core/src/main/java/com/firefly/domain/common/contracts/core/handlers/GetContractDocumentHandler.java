package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.domain.common.contracts.core.queries.GetContractDocumentQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link GetContractDocumentQuery} by retrieving a specific document from a contract.
 */
@QueryHandlerComponent
public class GetContractDocumentHandler extends QueryHandler<GetContractDocumentQuery, Object> {

    private final ContractDocumentsApi contractDocumentsApi;

    public GetContractDocumentHandler(ContractDocumentsApi contractDocumentsApi) {
        this.contractDocumentsApi = contractDocumentsApi;
    }

    @Override
    protected Mono<Object> doHandle(GetContractDocumentQuery query) {
        return contractDocumentsApi.getContractDocumentById(query.getContractId(), query.getDocumentId())
                .map(dto -> dto);
    }
}
