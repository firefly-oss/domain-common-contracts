package com.firefly.domain.common.contracts.core.workflows;

import com.firefly.commons.ecm.sdk.api.DocumentControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentDTO;
import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractDocumentDTO;
import com.firefly.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import com.firefly.domain.common.contracts.core.TestContractsCoreApplication;
import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private DocumentControllerApi documentControllerApi;

    @MockBean
    private ContractDocumentsApi contractDocumentsApi;

    // ─── Required by other beans in the same Spring context ───────────────────

    @MockBean
    private ContractsApi contractsApi;

    @MockBean
    private ContractPartiesApi contractPartiesApi;

    @MockBean
    private GlobalContractPartiesApi globalContractPartiesApi;

    @MockBean
    private ContractTermsApi contractTermsApi;

    @MockBean
    private ContractStatusHistoryApi contractStatusHistoryApi;

    @MockBean
    private DocumentSignatureControllerApi documentSignatureControllerApi;

    @MockBean
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
