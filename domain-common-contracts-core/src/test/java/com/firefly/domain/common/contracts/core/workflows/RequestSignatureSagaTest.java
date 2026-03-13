package com.firefly.domain.common.contracts.core.workflows;

import com.firefly.commons.ecm.sdk.api.DocumentControllerApi;
import com.firefly.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentSignatureDTO;
import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.core.contract.sdk.api.ContractsApi;
import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.TestContractsCoreApplication;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequestResult;
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
 * Integration tests for {@link RequestSignatureSaga}.
 * Uses the real annotation-processed saga bean with mocked SDK dependencies.
 */
@SpringBootTest(classes = TestContractsCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequestSignatureSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    // ─── Direct saga dependencies ─────────────────────────────────────────────

    @MockBean
    private DocumentSignatureControllerApi documentSignatureControllerApi;

    @MockBean
    private ESignaturePort eSignaturePort;

    @MockBean
    private ContractStatusHistoryApi contractStatusHistoryApi;

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
    private ContractDocumentsApi contractDocumentsApi;

    @MockBean
    private DocumentControllerApi documentControllerApi;

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Test
    void execute_happyPath_shouldCreateRecordSendToProviderAndUpdateStatus() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID signatureRecordId = UUID.randomUUID();
        UUID statusHistoryId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(documentSignatureControllerApi.addDocumentSignature(eq(documentId), any(DocumentSignatureDTO.class)))
                .thenReturn(Mono.just(new DocumentSignatureDTO(signatureRecordId, null, null, null, null, null, null, null)));

        when(eSignaturePort.requestSignature(any()))
                .thenReturn(Mono.just(SignatureRequestResult.builder()
                        .signatureRequestId(UUID.randomUUID().toString())
                        .provider("STUB")
                        .status("REQUESTED")
                        .build()));

        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class)))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(statusHistoryId, null, null)));

        StepInputs inputs = StepInputs.builder()
                .forStepId("create-signature-request", cmd)
                .forStepId("send-to-provider", cmd)
                .forStepId("update-contract-status", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("request-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.compensatedSteps()).isEmpty();
                    assertThat(result.resultOf("update-contract-status", UUID.class)).contains(statusHistoryId);
                })
                .verifyComplete();
    }

    // ─── Compensation: create-signature-request fails ─────────────────────────

    @Test
    void execute_whenCreateSignatureRequestFails_shouldNotCallSubsequentSteps() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(documentSignatureControllerApi.addDocumentSignature(eq(documentId), any(DocumentSignatureDTO.class)))
                .thenReturn(Mono.error(new RuntimeException("Document service unavailable")));

        StepInputs inputs = StepInputs.builder()
                .forStepId("create-signature-request", cmd)
                .forStepId("send-to-provider", cmd)
                .forStepId("update-contract-status", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("request-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("create-signature-request");
                })
                .verifyComplete();

        verify(eSignaturePort, never()).requestSignature(any());
        verify(contractStatusHistoryApi, never()).createContractStatusHistory(any(), any());
    }

    // ─── Compensation: send-to-provider fails → cancel signature record ────────

    @Test
    void execute_whenSendToProviderFails_shouldCompensateByCancellingSignatureRecord() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID signatureRecordId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(documentSignatureControllerApi.addDocumentSignature(eq(documentId), any(DocumentSignatureDTO.class)))
                .thenReturn(Mono.just(new DocumentSignatureDTO(signatureRecordId, null, null, null, null, null, null, null)));
        when(eSignaturePort.requestSignature(any()))
                .thenReturn(Mono.error(new RuntimeException("Provider timeout")));
        when(documentSignatureControllerApi.deleteDocumentSignature(eq(documentId), eq(signatureRecordId)))
                .thenReturn(Mono.empty());

        StepInputs inputs = StepInputs.builder()
                .forStepId("create-signature-request", cmd)
                .forStepId("send-to-provider", cmd)
                .forStepId("update-contract-status", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("request-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("send-to-provider");
                    assertThat(result.compensatedSteps()).contains("create-signature-request");
                })
                .verifyComplete();

        verify(contractStatusHistoryApi, never()).createContractStatusHistory(any(), any());
    }

    // ─── Compensation: update-contract-status fails ───────────────────────────

    @Test
    void execute_whenUpdateContractStatusFails_shouldCompensatePriorSteps() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID signatureRecordId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(documentSignatureControllerApi.addDocumentSignature(eq(documentId), any(DocumentSignatureDTO.class)))
                .thenReturn(Mono.just(new DocumentSignatureDTO(signatureRecordId, null, null, null, null, null, null, null)));
        when(eSignaturePort.requestSignature(any()))
                .thenReturn(Mono.just(SignatureRequestResult.builder()
                        .signatureRequestId(UUID.randomUUID().toString())
                        .provider("STUB").status("REQUESTED").build()));
        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any()))
                .thenReturn(Mono.error(new RuntimeException("Contract service down")));
        when(documentSignatureControllerApi.deleteDocumentSignature(eq(documentId), eq(signatureRecordId)))
                .thenReturn(Mono.empty());

        StepInputs inputs = StepInputs.builder()
                .forStepId("create-signature-request", cmd)
                .forStepId("send-to-provider", cmd)
                .forStepId("update-contract-status", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("request-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("update-contract-status");
                    assertThat(result.compensatedSteps()).contains("create-signature-request");
                })
                .verifyComplete();
    }
}
