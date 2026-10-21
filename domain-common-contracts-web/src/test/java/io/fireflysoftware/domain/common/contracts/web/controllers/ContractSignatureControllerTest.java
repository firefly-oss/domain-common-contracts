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

import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractSignatureControllerTest {

    @Mock
    private ContractService contractService;

    private WebTestClient webTestClient;

    private static final UUID CONTRACT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ContractSignatureController(contractService)).build();
    }

    @Test
    void requestSignature_shouldReturn202WithRequestId() {
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(CONTRACT_ID, documentId, signerPartyId);

        when(contractService.requestContractSignature(CONTRACT_ID, documentId, signerPartyId))
                .thenReturn(Mono.just(requestId));

        webTestClient.post().uri("/api/v1/contracts/{contractId}/signature/request", CONTRACT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(UUID.class).isEqualTo(requestId);

        verify(contractService).requestContractSignature(CONTRACT_ID, documentId, signerPartyId);
    }

    @Test
    void confirmSignature_shouldReturn200() {
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(CONTRACT_ID, "sig-req-123");

        when(contractService.confirmContractSignature(CONTRACT_ID, "sig-req-123"))
                .thenReturn(Mono.empty());

        webTestClient.post().uri("/api/v1/contracts/{contractId}/signature/confirm", CONTRACT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isOk();

        verify(contractService).confirmContractSignature(CONTRACT_ID, "sig-req-123");
    }
}
