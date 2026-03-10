package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.commons.ecm.sdk.api.DocumentControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentDTO;
import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.model.ContractDocumentDTO;
import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
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
class GenerateContractDocumentHandlerTest {

    @Mock
    private DocumentControllerApi documentControllerApi;

    @Mock
    private ContractDocumentsApi contractDocumentsApi;

    private GenerateContractDocumentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GenerateContractDocumentHandler(documentControllerApi, contractDocumentsApi);
    }

    @Test
    void doHandle_shouldCreateDocumentAndLinkToContract() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID contractDocId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        DocumentDTO createdDoc = new DocumentDTO(docId);
        when(documentControllerApi.createDocument(any(DocumentDTO.class)))
                .thenReturn(Mono.just(createdDoc));

        ContractDocumentDTO contractDocDto = new ContractDocumentDTO(contractDocId, null, null);
        when(contractDocumentsApi.createContractDocument(eq(contractId), any(ContractDocumentDTO.class)))
                .thenReturn(Mono.just(contractDocDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(contractDocId)
                .verifyComplete();

        verify(documentControllerApi).createDocument(any(DocumentDTO.class));

        ArgumentCaptor<ContractDocumentDTO> captor = ArgumentCaptor.forClass(ContractDocumentDTO.class);
        verify(contractDocumentsApi).createContractDocument(eq(contractId), captor.capture());
        assertThat(captor.getValue().getDocumentId()).isEqualTo(docId);
        assertThat(captor.getValue().getContractId()).isEqualTo(contractId);
        assertThat(captor.getValue().getDocumentTypeId()).isEqualTo(templateId);
    }
}
