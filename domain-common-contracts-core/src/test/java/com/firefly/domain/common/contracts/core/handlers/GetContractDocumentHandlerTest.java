package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.model.ContractDocumentDTO;
import com.firefly.domain.common.contracts.core.queries.GetContractDocumentQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

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
        when(contractDocumentsApi.getContractDocumentById(contractId, documentId))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(query))
                .expectNextMatches(result -> result instanceof ContractDocumentDTO)
                .verifyComplete();

        verify(contractDocumentsApi).getContractDocumentById(contractId, documentId);
    }
}
