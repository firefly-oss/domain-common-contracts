package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.PaginationResponse;
import com.firefly.domain.common.contracts.core.queries.GetContractStatusQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetContractStatusHandlerTest {

    @Mock
    private ContractStatusHistoryApi contractStatusHistoryApi;

    private GetContractStatusHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetContractStatusHandler(contractStatusHistoryApi);
    }

    @Test
    void doHandle_shouldReturnFirstStatusHistoryEntry() {
        UUID contractId = UUID.randomUUID();
        GetContractStatusQuery query = new GetContractStatusQuery(contractId);

        PaginationResponse response = new PaginationResponse();
        response.setContent(List.of("latestStatus", "olderStatus"));

        when(contractStatusHistoryApi.filterContractStatusHistory(contractId, null))
                .thenReturn(Mono.just(response));

        StepVerifier.create(handler.doHandle(query))
                .expectNext("latestStatus")
                .verifyComplete();

        verify(contractStatusHistoryApi).filterContractStatusHistory(contractId, null);
    }
}
