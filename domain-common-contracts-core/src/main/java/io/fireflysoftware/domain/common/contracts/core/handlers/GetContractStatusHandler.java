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

import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractStatusQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;
import java.util.UUID;

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
        return contractStatusHistoryApi.filterContractStatusHistory(query.getContractId(), null, UUID.randomUUID().toString())
                .mapNotNull(response -> {
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        return response.getContent().getFirst();
                    }
                    return null;
                });
    }
}
