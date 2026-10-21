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
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentDTO;
import io.fireflysoftware.domain.common.contracts.core.queries.GetContractDocumentQuery;
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
class GetContractDocumentHandlerTest {

    @Mock
    private ContractDocumentsApi contractDocumentsApi;

    private GetContractDocumentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractDocumentHandler(contractDocumentsApi);
    }

    @Test
    void doHandle_shouldCallGetContractDocumentByIdAndReturnResult() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        GetContractDocumentQuery query = new GetContractDocumentQuery(contractId, documentId);

        ContractDocumentDTO responseDto = new ContractDocumentDTO(documentId, null, null);
        when(contractDocumentsApi.getContractDocumentById(eq(contractId), eq(documentId), any()))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(query))
                .expectNextMatches(result -> result instanceof ContractDocumentDTO)
                .verifyComplete();

        verify(contractDocumentsApi).getContractDocumentById(eq(contractId), eq(documentId), any());
    }
}
