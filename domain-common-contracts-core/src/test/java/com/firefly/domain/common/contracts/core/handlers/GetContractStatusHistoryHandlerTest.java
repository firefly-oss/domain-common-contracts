package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.PaginationResponse;
import com.firefly.domain.common.contracts.core.queries.GetContractStatusHistoryQuery;
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
class GetContractStatusHistoryHandlerTest {

    @Mock
    private ContractStatusHistoryApi contractStatusHistoryApi;

    private GetContractStatusHistoryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractStatusHistoryHandler(contractStatusHistoryApi);
    }

    @Test
    void doHandle_shouldReturnAllStatusHistoryEntries() {
        UUID contractId = UUID.randomUUID();
        GetContractStatusHistoryQuery query = new GetContractStatusHistoryQuery(contractId);

        PaginationResponse response = new PaginationResponse();
        response.setContent(List.of("status1", "status2"));

        when(contractStatusHistoryApi.filterContractStatusHistory(any(), any(), any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(handler.doHandle(query))
                .assertNext(results -> assertThat(results).hasSize(2))
                .verifyComplete();

        verify(contractStatusHistoryApi).filterContractStatusHistory(any(), any(), any());
    }
}
