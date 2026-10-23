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
import io.fireflysoftware.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import io.fireflysoftware.core.contract.sdk.api.ContractEventsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.api.GlobalContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractEventDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.domain.common.contracts.core.TestContractsCoreApplication;
import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

    @MockitoBean
    private ContractEventsApi contractEventsApi;

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
    private ContractLinesApi contractLinesApi;

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
                        .provider("STUB").tenantId("synthetic-tenant").synthetic(true)
                        .contractId(contractId).documentId(UUID.randomUUID()).signerPartyId(UUID.randomUUID())
                        .build()));

        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class), any()))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(statusHistoryId, null, null)));
        when(contractEventsApi.createContractEvent(eq(contractId), any(ContractEventDTO.class), any()))
                .thenReturn(Mono.just(signedEvent()));

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

    @Test
    void verifiedSignatureFromAnotherContractCannotApproveTheRequestedContract() {
        String requestId=UUID.randomUUID().toString();
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(requestId).status("VERIFIED").provider("sandbox").contractId(UUID.randomUUID())
                .documentId(UUID.randomUUID()).signerPartyId(UUID.randomUUID()).build()));
        var cmd=new ConfirmContractSignatureCommand(UUID.randomUUID(),requestId);
        assertVerificationRejected(cmd);
    }

    // ─── Compensation: verify-signature fails ─────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "PENDING", "DECLINED", "EXPIRED", "CANCELED", "UNKNOWN", "verified", ""})
    void execute_whenProviderHasNotVerifiedSignature_shouldNotApproveContract(String status) {
        String requestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(requestId).status(status).provider("TEST").build()));

        assertVerificationRejected(cmd);
    }

    @Test
    void execute_whenProviderReturnsAnotherRequest_shouldNotApproveContract() {
        String requestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(UUID.randomUUID().toString()).status("VERIFIED").provider("TEST").build()));

        assertVerificationRejected(cmd);
    }

    @Test
    void execute_whenProviderReturnsNoVerification_shouldNotApproveContract() {
        String requestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.empty());

        assertVerificationRejected(cmd);
    }

    private void assertVerificationRejected(ConfirmContractSignatureCommand cmd) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();
        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isFalse();
                    assertThat(result.firstErrorStepId()).contains("verify-signature");
                }).verifyComplete();
        verify(contractStatusHistoryApi, never()).createContractStatusHistory(any(), any(), any());
        verify(contractEventsApi, never()).createContractEvent(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void execute_whenRequestIdentityIsAbsent_shouldNotAskProviderOrApprove(String requestId) {
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(requestId).status("VERIFIED").provider("TEST").build()));

        assertVerificationRejected(cmd);
        verify(eSignaturePort, never()).verifySignature(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void execute_whenProviderIdentityIsAbsent_shouldNotApprove(String provider) {
        String requestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(requestId).status("VERIFIED").provider(provider).build()));

        assertVerificationRejected(cmd);
    }

    @Test
    void execute_whenProviderStatusIsAbsent_shouldNotApprove() {
        String requestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(UUID.randomUUID(), requestId);
        when(eSignaturePort.verifySignature(requestId)).thenReturn(Mono.just(SignatureVerificationResult.builder()
                .signatureRequestId(requestId).provider("TEST").build()));

        assertVerificationRejected(cmd);
    }

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
                        .provider("STUB").tenantId("synthetic-tenant").synthetic(true)
                        .contractId(contractId).documentId(UUID.randomUUID()).signerPartyId(UUID.randomUUID())
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
                        .provider("STUB").tenantId("synthetic-tenant").synthetic(true)
                        .contractId(contractId).documentId(UUID.randomUUID()).signerPartyId(UUID.randomUUID())
                        .build()));

        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class), any()))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(statusHistoryId, null, null)));
        when(contractEventsApi.createContractEvent(eq(contractId), any(ContractEventDTO.class), any()))
                .thenReturn(Mono.just(signedEvent()));

        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        // The signed event is recorded on the contract after status approval.
        StepVerifier.create(sagaEngine.execute("confirm-signature-saga", inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.compensatedSteps()).isEmpty();
                })
                .verifyComplete();
    }

    private static ContractEventDTO signedEvent() {
        ContractEventDTO event = new ContractEventDTO(UUID.randomUUID(), null, null);
        event.setEventType(ContractEventDTO.EventTypeEnum.CONTRACT_SIGNED);
        return event;
    }
}
