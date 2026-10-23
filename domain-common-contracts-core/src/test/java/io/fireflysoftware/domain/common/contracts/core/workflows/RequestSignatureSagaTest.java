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
import io.fireflysoftware.commons.ecm.sdk.model.DocumentSignatureDTO;
import io.fireflysoftware.core.contract.sdk.api.ContractEventsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractDocumentsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.api.GlobalContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.domain.common.contracts.core.TestContractsCoreApplication;
import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequestResult;
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
 * Integration tests for {@link RequestSignatureSaga}.
 * Uses the real annotation-processed saga bean with mocked SDK dependencies.
 */
@SpringBootTest(classes = TestContractsCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequestSignatureSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    // ─── Direct saga dependencies ─────────────────────────────────────────────

    @MockitoBean
    private DocumentSignatureControllerApi documentSignatureControllerApi;

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

    @Test
    void execute_returnsTheDurableRequestIdWithoutCreatingAnotherCoreRecord() {
        UUID contract=UUID.randomUUID(),document=UUID.randomUUID(),signer=UUID.randomUUID(),id=UUID.randomUUID();
        var cmd=new RequestContractSignatureCommand(contract,document,signer);
        when(eSignaturePort.requestSignature(any())).thenReturn(Mono.just(SignatureRequestResult.builder()
                .signatureRequestId(id.toString()).provider("sandbox").status("PENDING").build()));
        when(contractStatusHistoryApi.createContractStatusHistory(eq(contract),any(),any()))
                .thenReturn(Mono.just(new ContractStatusHistoryDTO(UUID.randomUUID(),null,null)));
        var inputs=StepInputs.builder().forStepId("create-signature-request",cmd).forStepId("send-to-provider",cmd)
                .forStepId("update-contract-status",cmd).build();
        StepVerifier.create(sagaEngine.execute("request-signature-saga",inputs)).assertNext(result -> {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.resultOf("create-signature-request",UUID.class)).contains(id);
        }).verifyComplete();
        verify(documentSignatureControllerApi,never()).addDocumentSignature(any(),any(),any());
        verify(eSignaturePort).requestSignature(any());
    }

    @Test
    void missingProviderRequestIdFailsBeforeContractStatusMutation() {
        var cmd=new RequestContractSignatureCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        when(eSignaturePort.requestSignature(any())).thenReturn(Mono.just(SignatureRequestResult.builder()
                .provider("sandbox").status("PENDING").build()));
        var inputs=StepInputs.builder().forStepId("create-signature-request",cmd).forStepId("send-to-provider",cmd)
                .forStepId("update-contract-status",cmd).build();
        StepVerifier.create(sagaEngine.execute("request-signature-saga",inputs)).assertNext(result -> assertThat(result.isFailed()).isTrue()).verifyComplete();
        verify(contractStatusHistoryApi,never()).createContractStatusHistory(any(),any(),any());
    }

    @Test
    void failedContractUpdateRetainsDurableProviderRequestForRecovery() {
        var cmd=new RequestContractSignatureCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        UUID id=UUID.randomUUID();
        when(eSignaturePort.requestSignature(any())).thenReturn(Mono.just(SignatureRequestResult.builder()
                .signatureRequestId(id.toString()).provider("sandbox").status("PENDING").build()));
        when(contractStatusHistoryApi.createContractStatusHistory(any(),any(),any())).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        var inputs=StepInputs.builder().forStepId("create-signature-request",cmd).forStepId("send-to-provider",cmd)
                .forStepId("update-contract-status",cmd).build();
        StepVerifier.create(sagaEngine.execute("request-signature-saga",inputs)).assertNext(result -> {
            assertThat(result.isFailed()).isTrue();assertThat(result.compensatedSteps()).isEmpty();
        }).verifyComplete();
        verify(documentSignatureControllerApi,never()).deleteDocumentSignature(any(),any(),any());
    }
}
