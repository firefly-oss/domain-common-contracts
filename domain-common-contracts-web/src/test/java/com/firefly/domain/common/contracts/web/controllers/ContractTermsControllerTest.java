package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
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
class ContractTermsControllerTest {

    @Mock
    private ContractService contractService;

    private WebTestClient webTestClient;

    private static final UUID CONTRACT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ContractTermsController(contractService)).build();
    }

    @Test
    void addTerms_shouldReturn201WithTermId() {
        UUID termTemplateId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        AddContractTermsCommand cmd = new AddContractTermsCommand(CONTRACT_ID, termTemplateId, "4.75");

        when(contractService.addContractTerms(CONTRACT_ID, termTemplateId, "4.75"))
                .thenReturn(Mono.just(termId));

        webTestClient.post().uri("/api/v1/contracts/{contractId}/terms", CONTRACT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UUID.class).isEqualTo(termId);

        verify(contractService).addContractTerms(CONTRACT_ID, termTemplateId, "4.75");
    }

    @Test
    void listTerms_shouldReturn200WithTerms() {
        List<Object> terms = List.of("term-1", "term-2");

        when(contractService.getContractTerms(CONTRACT_ID)).thenReturn(Mono.just(terms));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/terms", CONTRACT_ID)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getContractTerms(CONTRACT_ID);
    }
}
