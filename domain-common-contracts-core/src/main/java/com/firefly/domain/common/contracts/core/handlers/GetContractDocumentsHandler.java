package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.domain.common.contracts.core.queries.GetContractDocumentsQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Handles {@link GetContractDocumentsQuery} by retrieving all documents of a contract.
 */
@QueryHandlerComponent
public class GetContractDocumentsHandler extends QueryHandler<GetContractDocumentsQuery, List<Object>> {

    private final ContractDocumentsApi contractDocumentsApi;

    public GetContractDocumentsHandler(ContractDocumentsApi contractDocumentsApi) {
        this.contractDocumentsApi = contractDocumentsApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractDocumentsQuery query) {
        return contractDocumentsApi.filterContractDocuments(query.getContractId(), null, UUID.randomUUID().toString())
                .mapNotNull(response -> response.getContent() != null
                        ? response.getContent()
                        : List.of());
    }
}
