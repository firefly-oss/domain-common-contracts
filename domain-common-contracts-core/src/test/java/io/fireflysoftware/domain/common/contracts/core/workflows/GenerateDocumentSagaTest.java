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


package io.fireflysoftware.domain.common.contracts.core.workflows;

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentDTO;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.api.GlobalContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDocumentDTO;
import io.fireflysoftware.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import io.fireflysoftware.domain.common.contracts.core.TestContractsCoreApplication;
import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link GenerateDocumentSaga}.
 * Uses the real annotation-processed saga bean with mocked SDK dependencies.
 */
@SpringBootTest(classes = TestContractsCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GenerateDocumentSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    // ─── Direct saga dependencies ─────────────────────────────────────────────

    @MockitoBean
    private DocumentControllerApi documentControllerApi;

    @MockitoBean
    private ContractDocumentsApi contractDocumentsApi;

    @MockitoBean
    private ContractLinesApi contractLinesApi;

    // ─── Required by other beans in the same Spring context ───────────────────

    @MockitoBean
    private ContractsApi contractsApi;

    @MockitoBean
    private ContractPartiesApi contractPartiesApi;

    @MockitoBean
    private GlobalContractPartiesApi globalContractPartiesApi;

    @MockitoBean
    private ContractTermsApi contractTermsApi;

    @MockitoBean
    private ContractStatusHistoryApi contractStatusHistoryApi;

    @MockitoBean
    private DocumentSignatureControllerApi documentSignatureControllerApi;

    @MockitoBean
    private ESignaturePort eSignaturePort;

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Test
    void execute_happyPath_shouldCreateDocumentAndAttachToContract() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID contractDocumentId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        when(documentControllerApi.createDocument(any(DocumentDTO.class), any()))
                .thenReturn(Mono.just(new DocumentDTO(documentId)));

        ContractDocumentDTO contractDocDto = new ContractDocumentDTO(contractDocumentId, null, null);
        when(contractDocumentsApi.createContractDocument(eq(contractId), any(ContractDocumentDTO.class), any()))
                .thenReturn(Mono.just(contractDocDto));

        StepInputs inputs = StepInputs.builder()
                .forStepId("generate-document", cmd)
                .forStepId("attach-to-contract", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("generate-document-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.compensatedSteps()).isEmpty();
                    assertThat(result.resultOf("attach-to-contract", UUID.class)).contains(contractDocumentId);
                })
                .verifyComplete();
    }

    // ─── Compensation: generate-document fails ─────────────────────────────────

    @Test
    void execute_whenGenerateDocumentFails_shouldNotCallAttachAndNoCompensation() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        when(documentControllerApi.createDocument(any(DocumentDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("ECM unavailable")));

        StepInputs inputs = StepInputs.builder()
                .forStepId("generate-document", cmd)
                .forStepId("attach-to-contract", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("generate-document-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("generate-document");
                    // generate-document has no completed state to compensate; attach-to-contract never ran
                    assertThat(result.compensatedSteps()).isEmpty();
                })
                .verifyComplete();

        verify(contractDocumentsApi, never()).createContractDocument(any(), any(), any());
    }

    // ─── Compensation: attach-to-contract fails → compensate generate-document ─

    @Test
    void execute_whenAttachToContractFails_shouldCompensateByDeletingDocument() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        when(documentControllerApi.createDocument(any(DocumentDTO.class), any()))
                .thenReturn(Mono.just(new DocumentDTO(documentId)));
        when(contractDocumentsApi.createContractDocument(eq(contractId), any(ContractDocumentDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("Contract service unavailable")));
        when(documentControllerApi.deleteDocument(eq(documentId), any()))
                .thenReturn(Mono.empty());

        StepInputs inputs = StepInputs.builder()
                .forStepId("generate-document", cmd)
                .forStepId("attach-to-contract", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("generate-document-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("attach-to-contract");
                    // generate-document completed → its compensation (deleteDocument) must have run
                    assertThat(result.compensatedSteps()).contains("generate-document");
                })
                .verifyComplete();

        verify(documentControllerApi).deleteDocument(eq(documentId), any());
    }
}
