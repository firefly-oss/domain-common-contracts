package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.model.PaginationResponse;
import com.firefly.domain.common.contracts.core.queries.GetContractDocumentsQuery;
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
class GetContractDocumentsHandlerTest {

    @Mock
    private ContractDocumentsApi contractDocumentsApi;

    private GetContractDocumentsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractDocumentsHandler(contractDocumentsApi);
    }

    @Test
    void doHandle_shouldCallFilterContractDocumentsAndReturnContent() {
        UUID contractId = UUID.randomUUID();
        GetContractDocumentsQuery query = new GetContractDocumentsQuery(contractId);

        PaginationResponse response = new PaginationResponse();
        response.setContent(List.of("doc1", "doc2"));

        when(contractDocumentsApi.filterContractDocuments(any(), any(), any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(handler.doHandle(query))
                .assertNext(results -> assertThat(results).hasSize(2))
                .verifyComplete();

        verify(contractDocumentsApi).filterContractDocuments(any(), any(), any());
    }
}
