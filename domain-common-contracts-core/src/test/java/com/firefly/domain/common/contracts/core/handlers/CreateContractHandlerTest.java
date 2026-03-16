package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
import com.firefly.domain.common.contracts.core.mappers.ContractCommandMapper;
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
class CreateContractHandlerTest {

    @Mock
    private ContractsApi contractsApi;

    @Mock
    private ContractCommandMapper mapper;

    private CreateContractHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateContractHandler(contractsApi, mapper);
    }

    @Test
    void doHandle_shouldCallCreateContractAndReturnId() {
        UUID partyId = UUID.randomUUID();
        UUID expectedId = UUID.randomUUID();
        CreateContractCommand cmd = new CreateContractCommand(partyId, "LOAN", "Test contract");

        ContractDTO mappedDto = new ContractDTO(null, null, null);
        ContractDTO responseDto = new ContractDTO(expectedId, null, null);
        when(mapper.toContractDto(cmd)).thenReturn(mappedDto);
        when(contractsApi.createContract(eq(mappedDto), any())).thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedId)
                .verifyComplete();

        verify(mapper).toContractDto(cmd);
        verify(contractsApi).createContract(eq(mappedDto), any());
    }
}
