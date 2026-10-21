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
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractDetailQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetContractDetailHandlerTest {

    @Mock
    private ContractsApi contractsApi;

    private GetContractDetailHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractDetailHandler(contractsApi);
    }

    @Test
    void doHandle_shouldCallGetContractByIdAndReturnResult() {
        UUID contractId = UUID.randomUUID();
        GetContractDetailQuery query = new GetContractDetailQuery(contractId);

        ContractDTO responseDto = new ContractDTO(contractId, null, null);
        when(contractsApi.getContractById(eq(contractId), any())).thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(query))
                .expectNextMatches(result -> result instanceof ContractDTO dto
                        && dto.getContractId().equals(contractId))
                .verifyComplete();

        verify(contractsApi).getContractById(eq(contractId), any());
    }
}
