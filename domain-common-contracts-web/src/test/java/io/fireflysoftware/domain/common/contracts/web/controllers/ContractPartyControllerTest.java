/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.contracts.web.controllers;

import io.fireflysoftware.domain.common.contracts.core.commands.AddContractPartyCommand;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
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
