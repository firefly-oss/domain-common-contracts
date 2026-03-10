package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateContractHandlerTest {

    @Mock
    private ContractsApi contractsApi;

    private CreateContractHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateContractHandler(contractsApi);
    }

    @Test
    void doHandle_shouldCallCreateContractAndReturnId() {
        UUID partyId = UUID.randomUUID();
        UUID expectedId = UUID.randomUUID();
        CreateContractCommand cmd = new CreateContractCommand(partyId, "LOAN", "Test contract");

        ContractDTO responseDto = new ContractDTO(expectedId, null, null);
        when(contractsApi.createContract(any(ContractDTO.class))).thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedId)
                .verifyComplete();

        ArgumentCaptor<ContractDTO> captor = ArgumentCaptor.forClass(ContractDTO.class);
        verify(contractsApi).createContract(captor.capture());
        assertThat(captor.getValue().getContractNumber()).isEqualTo("LOAN");
    }
}
