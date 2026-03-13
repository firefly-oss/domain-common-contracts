package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
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
class ContractDocumentControllerTest {

    @Mock
    private ContractService contractService;

    private WebTestClient webTestClient;

    private static final UUID CONTRACT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ContractDocumentController(contractService)).build();
    }

    @Test
    void generateDocument_shouldReturn202WithDocumentId() {
        UUID templateId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(CONTRACT_ID, templateId);

        when(contractService.generateContractDocument(CONTRACT_ID, templateId))
                .thenReturn(Mono.just(documentId));

        webTestClient.post().uri("/api/v1/contracts/{contractId}/documents/generate", CONTRACT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(UUID.class).isEqualTo(documentId);

        verify(contractService).generateContractDocument(CONTRACT_ID, templateId);
    }

    @Test
    void listDocuments_shouldReturn200WithDocuments() {
        List<Object> documents = List.of("doc-1", "doc-2");

        when(contractService.getContractDocuments(CONTRACT_ID)).thenReturn(Mono.just(documents));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/documents", CONTRACT_ID)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).getContractDocuments(CONTRACT_ID);
    }

    @Test
    void getDocument_shouldReturn200WithDocument() {
        UUID docId = UUID.randomUUID();
        Object document = "document-detail";

        when(contractService.getContractDocument(CONTRACT_ID, docId)).thenReturn(Mono.just(document));

        webTestClient.get().uri("/api/v1/contracts/{contractId}/documents/{docId}", CONTRACT_ID, docId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("document-detail");

        verify(contractService).getContractDocument(CONTRACT_ID, docId);
    }
}
