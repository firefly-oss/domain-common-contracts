package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
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
class RequestContractSignatureHandlerTest {

    @Mock
    private SagaEngine sagaEngine;

    @Mock
    private SagaResult sagaResult;

    private RequestContractSignatureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RequestContractSignatureHandler(sagaEngine);
    }

    @Test
    void doHandle_shouldDelegateToSagaAndReturnStatusHistoryId() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();
        UUID statusHistoryId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(sagaResult.isFailed()).thenReturn(false);
        when(sagaResult.resultOf(eq("update-contract-status"), eq(UUID.class)))
                .thenReturn(Optional.of(statusHistoryId));
        when(sagaEngine.execute(eq("request-signature-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(statusHistoryId)
                .verifyComplete();

        verify(sagaEngine).execute(eq("request-signature-saga"), any(StepInputs.class));
    }

    @Test
    void doHandle_whenSagaFails_shouldEmitError() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID signerPartyId = UUID.randomUUID();

        RequestContractSignatureCommand cmd = new RequestContractSignatureCommand(contractId, documentId, signerPartyId);

        when(sagaResult.isFailed()).thenReturn(true);
        when(sagaResult.firstErrorStepId()).thenReturn(Optional.of("send-to-provider"));
        when(sagaEngine.execute(eq("request-signature-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorMatches(e -> e.getMessage().contains("request-signature-saga failed"))
                .verify();
    }
}
