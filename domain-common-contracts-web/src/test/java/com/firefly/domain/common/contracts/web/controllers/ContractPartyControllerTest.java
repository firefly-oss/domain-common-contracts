package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
import com.firefly.domain.common.contracts.core.services.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractPartyControllerTest {

    @Mock
    private ContractService contractService;

    private WebTestClient webTestClient;

    private static final UUID CONTRACT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ContractPartyController(contractService)).build();
    }

    @Test
    void addParty_shouldReturn201WithPartyId() {
        UUID partyId = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        AddContractPartyCommand cmd = new AddContractPartyCommand(CONTRACT_ID, partyId, "BORROWER");

        when(contractService.addContractParty(CONTRACT_ID, partyId, "BORROWER"))
                .thenReturn(Mono.just(createdId));

        webTestClient.post().uri("/api/v1/contracts/{contractId}/parties", CONTRACT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UUID.class).isEqualTo(createdId);

        verify(contractService).addContractParty(CONTRACT_ID, partyId, "BORROWER");
    }

    @Test
    void listParties_shouldReturn200WithParties() {
        List<Object> parties = List.of("party-1", "party-2");

        when(contractService.getContractParties(CONTRACT_ID)).thenReturn(Mono.just(parties));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/parties", CONTRACT_ID)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getContractParties(CONTRACT_ID);
    }

    @Test
    void removeParty_shouldReturn204() {
        UUID partyId = UUID.randomUUID();

        when(contractService.removeContractParty(CONTRACT_ID, partyId)).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/v1/contracts/{contractId}/parties/{partyId}", CONTRACT_ID, partyId)
                .exchange()
                .expectStatus().isNoContent();

        verify(contractService).removeContractParty(CONTRACT_ID, partyId);
    }
}
