package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.domain.common.contracts.core.commands.RemoveContractPartyCommand;
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
class RemoveContractPartyHandlerTest {

    @Mock
    private ContractPartiesApi contractPartiesApi;

    private RemoveContractPartyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoveContractPartyHandler(contractPartiesApi);
    }

    @Test
    void doHandle_shouldCallDeleteContractParty() {
        UUID contractId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        RemoveContractPartyCommand cmd = new RemoveContractPartyCommand(contractId, partyId);

        when(contractPartiesApi.deleteContractParty(contractId, partyId)).thenReturn(Mono.empty());

        StepVerifier.create(handler.doHandle(cmd))
                .verifyComplete();

        verify(contractPartiesApi).deleteContractParty(contractId, partyId);
    }
}
