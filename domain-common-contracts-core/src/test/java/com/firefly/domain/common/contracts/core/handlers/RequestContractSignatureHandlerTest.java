package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequestResult;
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
class RequestContractSignatureHandlerTest {

    @Mock
    private ESignaturePort eSignaturePort;

    @Mock
    private ContractStatusHistoryApi contractStatusHistoryApi;

    private RequestContractSignatureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RequestContractSignatureHandler(eSignaturePort, contractStatusHistoryApi);
    }

    @Test
    void doHandle_shouldRequestSignatureAndRecordStatus() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID statusHistoryId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        SignatureRequestResult sigResult = SignatureRequestResult.builder()
                .signatureRequestId(UUID.randomUUID().toString())
                .provider("STUB")
                .status("REQUESTED")
                .build();
        when(eSignaturePort.requestSignature(any(SignatureRequest.class)))
                .thenReturn(Mono.just(sigResult));

        ContractStatusHistoryDTO statusResponse = new ContractStatusHistoryDTO(statusHistoryId, null, null);
        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class)))
                .thenReturn(Mono.just(statusResponse));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(statusHistoryId)
                .verifyComplete();

        ArgumentCaptor<SignatureRequest> sigCaptor = ArgumentCaptor.forClass(SignatureRequest.class);
        verify(eSignaturePort).requestSignature(sigCaptor.capture());
        assertThat(sigCaptor.getValue().getContractId()).isEqualTo(contractId);
        assertThat(sigCaptor.getValue().getDocumentId()).isEqualTo(documentId);

        ArgumentCaptor<ContractStatusHistoryDTO> statusCaptor = ArgumentCaptor.forClass(ContractStatusHistoryDTO.class);
        verify(contractStatusHistoryApi).createContractStatusHistory(eq(contractId), statusCaptor.capture());
        assertThat(statusCaptor.getValue().getStatusCode())
                .isEqualTo(ContractStatusHistoryDTO.StatusCodeEnum.SUBMITTED_FOR_APPROVAL);
    }
}
