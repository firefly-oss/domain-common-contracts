package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddContractPartyHandlerTest {

    @Mock
    private ContractPartiesApi contractPartiesApi;

    private AddContractPartyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddContractPartyHandler(contractPartiesApi);
    }

    @Test
    void doHandle_shouldCallCreateContractPartyAndReturnId() {
        UUID contractId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID expectedPartyId = UUID.randomUUID();
        AddContractPartyCommand cmd = new AddContractPartyCommand(contractId, partyId, "BORROWER");

        ContractPartyDTO responseDto = new ContractPartyDTO(expectedPartyId, null, null);
        when(contractPartiesApi.createContractParty(eq(contractId), any(ContractPartyDTO.class)))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedPartyId)
                .verifyComplete();

        ArgumentCaptor<ContractPartyDTO> captor = ArgumentCaptor.forClass(ContractPartyDTO.class);
        verify(contractPartiesApi).createContractParty(eq(contractId), captor.capture());
        assertThat(captor.getValue().getPartyId()).isEqualTo(partyId);
        assertThat(captor.getValue().getContractId()).isEqualTo(contractId);
    }
}
