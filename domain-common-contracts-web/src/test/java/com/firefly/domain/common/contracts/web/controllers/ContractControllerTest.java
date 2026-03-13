package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
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
class ContractControllerTest {

    @Mock
    private ContractService contractService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ContractController(contractService)).build();
    }

    @Test
    void createContract_shouldReturn201WithContractId() {
        UUID partyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        CreateContractCommand cmd = new CreateContractCommand(partyId, "LOAN", "Test contract");

        when(contractService.createContract(partyId, "LOAN", "Test contract"))
                .thenReturn(Mono.just(contractId));

        webTestClient.post().uri("/api/v1/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UUID.class).isEqualTo(contractId);

        verify(contractService).createContract(partyId, "LOAN", "Test contract");
    }

    @Test
    void getContractDetail_shouldReturn200WithDetail() {
        UUID contractId = UUID.randomUUID();
        Object detail = "contract-detail";

        when(contractService.getContractDetail(contractId)).thenReturn(Mono.just(detail));

        webTestClient.get().uri("/api/v1/contracts/{contractId}", contractId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("contract-detail");

        verify(contractService).getContractDetail(contractId);
    }

    @Test
    void listByParty_shouldReturn200WithContracts() {
        UUID partyId = UUID.randomUUID();
        List<Object> contracts = List.of("contract-1", "contract-2");

        when(contractService.getContractsByParty(partyId)).thenReturn(Mono.just(contracts));

        webTestClient.get().uri("/api/v1/contracts/by-party/{partyId}", partyId)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getContractsByParty(partyId);
    }

    @Test
    void listActiveByParty_shouldReturn200WithActiveContracts() {
        UUID partyId = UUID.randomUUID();
        List<Object> active = List.of("active-contract");

        when(contractService.getActiveContractsByParty(partyId)).thenReturn(Mono.just(active));

        webTestClient.get().uri("/api/v1/contracts/by-party/{partyId}/active", partyId)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getActiveContractsByParty(partyId);
    }

    @Test
    void getStatus_shouldReturn200WithStatus() {
        UUID contractId = UUID.randomUUID();
        Object status = "ACTIVE";

        when(contractService.getContractStatus(contractId)).thenReturn(Mono.just(status));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/status", contractId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ACTIVE");

        verify(contractService).getContractStatus(contractId);
    }

    @Test
    void getStatusHistory_shouldReturn200WithHistory() {
        UUID contractId = UUID.randomUUID();
        List<Object> history = List.of("status-1", "status-2");

        when(contractService.getContractStatusHistory(contractId)).thenReturn(Mono.just(history));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/status-history", contractId)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getContractStatusHistory(contractId);
    }
}
