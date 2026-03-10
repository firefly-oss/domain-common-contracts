package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
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
class ConfirmContractSignatureHandlerTest {

    @Mock
    private ESignaturePort eSignaturePort;

    @Mock
    private ContractStatusHistoryApi contractStatusHistoryApi;

    private ConfirmContractSignatureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConfirmContractSignatureHandler(eSignaturePort, contractStatusHistoryApi);
    }

    @Test
    void doHandle_shouldVerifySignatureAndRecordApprovedStatus() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();
        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        SignatureVerificationResult verResult = SignatureVerificationResult.builder()
                .signatureRequestId(signatureRequestId)
                .status("VERIFIED")
                .provider("STUB")
                .build();
        when(eSignaturePort.verifySignature(signatureRequestId))
                .thenReturn(Mono.just(verResult));

        ContractStatusHistoryDTO statusResponse = new ContractStatusHistoryDTO(UUID.randomUUID(), null, null);
        when(contractStatusHistoryApi.createContractStatusHistory(eq(contractId), any(ContractStatusHistoryDTO.class)))
                .thenReturn(Mono.just(statusResponse));

        StepVerifier.create(handler.doHandle(cmd))
                .verifyComplete();

        verify(eSignaturePort).verifySignature(signatureRequestId);

        ArgumentCaptor<ContractStatusHistoryDTO> captor = ArgumentCaptor.forClass(ContractStatusHistoryDTO.class);
        verify(contractStatusHistoryApi).createContractStatusHistory(eq(contractId), captor.capture());
        assertThat(captor.getValue().getStatusCode())
                .isEqualTo(ContractStatusHistoryDTO.StatusCodeEnum.APPROVED);
    }
}
