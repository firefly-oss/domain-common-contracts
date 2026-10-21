/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractDocumentsQuery;
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
