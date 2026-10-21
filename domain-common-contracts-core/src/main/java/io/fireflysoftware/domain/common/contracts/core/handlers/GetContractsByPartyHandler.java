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

import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.api.GlobalContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractPartyDTO;
import io.fireflysoftware.core.product.sdk.api.ProductApi;
import io.fireflysoftware.core.product.sdk.model.ProductDTO;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractsByPartyQuery;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holdings for a party. The contract row comes from the contract core.
 * The product name and code come from the product core.
 */
@QueryHandlerComponent
public class GetContractsByPartyHandler extends QueryHandler<GetContractsByPartyQuery, List<Object>> {

    private final GlobalContractPartiesApi globalContractPartiesApi;
    private final ContractsApi contractsApi;
    private final ProductApi productApi;

    public GetContractsByPartyHandler(GlobalContractPartiesApi globalContractPartiesApi,
                                      ContractsApi contractsApi,
                                      ProductApi productApi) {
        this.globalContractPartiesApi = globalContractPartiesApi;
        this.contractsApi = contractsApi;
        this.productApi = productApi;
    }

    @Override
    protected Mono<List<Object>> doHandle(GetContractsByPartyQuery query) {
        return globalContractPartiesApi.getContractPartiesByPartyId(query.getPartyId(), null, key())
                .flatMap(response -> {
                    List<ContractPartyDTO> content = response.getContent() == null ? List.of() : response.getContent();
                    return Flux.fromIterable(content)
                            .concatMap(this::holding)
                            .collectList()
                            .map(rows -> {
                                List<Object> result = new ArrayList<>();
                                result.addAll(rows);
                                return result;
                            });
                });
    }

    private Mono<Map<String, Object>> holding(ContractPartyDTO party) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("contractPartyId", party.getContractPartyId());
        row.put("partyId", party.getPartyId());
        row.put("contractId", party.getContractId());
        if (party.getContractId() == null) {
            return Mono.just(row);
        }
        return contractsApi.getContractById(party.getContractId(), key())
                .flatMap(contract -> {
                    fillContract(row, contract);
                    if (contract.getProductId() == null) {
                        return Mono.just(row);
                    }
                    return productApi.getProductById(contract.getProductId(), key())
                            .map(product -> {
                                fillProduct(row, product);
                                return row;
                            })
                            .onErrorResume(ex -> Mono.just(row))
                            .defaultIfEmpty(row);
                })
                .defaultIfEmpty(row);
    }

    private static void fillContract(Map<String, Object> row, ContractDTO contract) {
        row.put("contractId", contract.getContractId() == null ? row.get("contractId") : contract.getContractId());
        row.put("contractNumber", contract.getContractNumber());
        row.put("contractStatus", contract.getContractStatus() == null ? null : contract.getContractStatus().getValue());
        row.put("productId", contract.getProductId());
    }

    private static void fillProduct(Map<String, Object> row, ProductDTO product) {
        row.put("productName", product.getProductName());
        row.put("productCode", product.getProductCode());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
