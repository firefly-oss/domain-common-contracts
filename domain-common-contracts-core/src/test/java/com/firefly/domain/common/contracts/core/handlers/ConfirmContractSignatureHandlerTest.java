package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmContractSignatureHandlerTest {

    @Mock
    private SagaEngine sagaEngine;

    @Mock
    private SagaResult sagaResult;

    private ConfirmContractSignatureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConfirmContractSignatureHandler(sagaEngine);
    }

    @Test
    void doHandle_shouldDelegateToSagaAndCompleteSuccessfully() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(sagaResult.isFailed()).thenReturn(false);
        when(sagaEngine.execute(eq("confirm-signature-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .verifyComplete();

        verify(sagaEngine).execute(eq("confirm-signature-saga"), any(StepInputs.class));
    }

    @Test
    void doHandle_whenSagaFails_shouldEmitError() {
        UUID contractId = UUID.randomUUID();
        String signatureRequestId = UUID.randomUUID().toString();

        ConfirmContractSignatureCommand cmd = new ConfirmContractSignatureCommand(contractId, signatureRequestId);

        when(sagaResult.isFailed()).thenReturn(true);
        when(sagaResult.firstErrorStepId()).thenReturn(Optional.of("verify-signature"));
        when(sagaEngine.execute(eq("confirm-signature-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorMatches(e -> e.getMessage().contains("confirm-signature-saga failed"))
                .verify();
    }
}
