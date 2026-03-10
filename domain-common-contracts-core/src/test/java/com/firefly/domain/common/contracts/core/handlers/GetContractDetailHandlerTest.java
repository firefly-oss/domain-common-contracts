package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.domain.common.contracts.core.queries.GetContractDetailQuery;
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
        when(contractsApi.getContractById(contractId)).thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(query))
                .expectNextMatches(result -> result instanceof ContractDTO dto
                        && dto.getContractId().equals(contractId))
                .verifyComplete();

        verify(contractsApi).getContractById(contractId);
    }
}
