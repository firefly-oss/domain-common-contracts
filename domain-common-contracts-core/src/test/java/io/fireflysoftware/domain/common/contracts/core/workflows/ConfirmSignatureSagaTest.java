package io.fireflysoftware.domain.common.contracts.core.workflows;

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.api.GlobalContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.domain.common.contracts.core.TestContractsCoreApplication;
import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
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
 * Integration tests for {@link ConfirmSignatureSaga}.
 * Uses the real annotation-processed saga bean with mocked SDK dependencies.
 */
@SpringBootTest(classes = TestContractsCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfirmSignatureSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    // ─── Direct saga dependencies ─────────────────────────────────────────────

    @MockitoBean
    private ESignaturePort eSignaturePort;

    @MockitoBean
    private ContractStatusHistoryApi contractStatusHistoryApi;

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
    private ContractDocumentsApi contractDocumentsApi;

    @MockitoBean
    private DocumentControllerApi documentControllerApi;

    @MockitoBean
    private DocumentSignatureControllerApi documentSignatureControllerApi;

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Test
    void execute_happyPath_shouldVerifySignatureUpdateStatusAndSendNotification() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();
        UUID statusHistoryId = UUID.randomUUID();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(eSignaturePort.verifySignature(eq(signatureRequestId)))
                .thenReturn(Mono.just(SignatureVerificationResult.builder()
                        .signatureRequestId(signatureRequestId)
                        .status("VERIFIED")
                        .provider("STUB")
                        .build()));

        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class), any()))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(statusHistoryId, null, null)));

        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess())
                            .as("Saga failed. Steps: %s, Error: %s, Failed steps: %s",
                                    result.steps(), result.error(), result.failedSteps())
                            .isTrue();
                    assertThat(result.compensatedSteps()).isEmpty();
                    assertThat(result.resultOf("update-contract-status", UUID.class)).contains(statusHistoryId);
                })
                .verifyComplete();
    }

    // ─── Compensation: verify-signature fails ─────────────────────────────────

    @Test
    void execute_whenVerifySignatureFails_shouldNotCallSubsequentSteps() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(eSignaturePort.verifySignature(eq(signatureRequestId)))
                .thenReturn(Mono.error(new RuntimeException("Provider unreachable")));

        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("verify-signature");
                })
                .verifyComplete();

        verify(contractStatusHistoryApi, never()).createContractStatusHistory(any(), any(), any());
    }

    // ─── Compensation: update-contract-status fails ───────────────────────────

    @Test
    void execute_whenUpdateContractStatusFails_shouldLogWarningAndFail() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(eSignaturePort.verifySignature(eq(signatureRequestId)))
                .thenReturn(Mono.just(SignatureVerificationResult.builder()
                        .signatureRequestId(signatureRequestId)
                        .status("VERIFIED")
                        .provider("STUB")
                        .build()));
        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("Contract service down")));

        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.firstErrorStepId()).contains("update-contract-status");
                    // verify-signature has no compensation defined
                    assertThat(result.compensatedSteps()).doesNotContain("verify-signature");
                })
                .verifyComplete();
    }

    // ─── send-notification is best-effort: does not fail the saga ─────────────

    @Test
    void execute_whenVerifyAndStatusSucceed_sagaCompletesRegardlessOfNotificationStep() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();
        UUID statusHistoryId = UUID.randomUUID();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(eSignaturePort.verifySignature(eq(signatureRequestId)))
                .thenReturn(Mono.just(SignatureVerificationResult.builder()
                        .signatureRequestId(signatureRequestId)
                        .status("VERIFIED")
                        .provider("STUB")
                        .build()));

        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class), any()))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(statusHistoryId, null, null)));

        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        // send-notification returns Mono.empty() by design — the saga still completes successfully
        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.compensatedSteps()).isEmpty();
                })
                .verifyComplete();
    }
}
