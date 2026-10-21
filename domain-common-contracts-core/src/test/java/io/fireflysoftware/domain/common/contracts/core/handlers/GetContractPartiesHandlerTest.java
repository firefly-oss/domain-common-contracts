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

import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.PaginationResponse;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractPartiesQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetContractPartiesHandlerTest {

    @Mock
    private ContractPartiesApi contractPartiesApi;

    private GetContractPartiesHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractPartiesHandler(contractPartiesApi);
    }

    @Test
    void doHandle_shouldCallFilterContractPartiesAndReturnContent() {
        UUID contractId = UUID.randomUUID();
        GetContractPartiesQuery query = new GetContractPartiesQuery(contractId);

        PaginationResponse response = new PaginationResponse();
        response.setContent(List.of("party1"));

        when(contractPartiesApi.filterContractParties(any(), any(), any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(handler.doHandle(query))
                .assertNext(results -> assertThat(results).hasSize(1))
                .verifyComplete();

        verify(contractPartiesApi).filterContractParties(any(), any(), any());
    }
}
