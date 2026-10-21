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


package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
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
class GenerateContractDocumentHandlerTest {

    @Mock
    private SagaEngine sagaEngine;

    @Mock
    private SagaResult sagaResult;

    private GenerateContractDocumentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GenerateContractDocumentHandler(sagaEngine);
    }

    @Test
    void doHandle_shouldDelegateToSagaAndReturnContractDocumentId() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID contractDocumentId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        when(sagaResult.isFailed()).thenReturn(false);
        when(sagaResult.resultOf(eq("attach-to-contract"), eq(UUID.class)))
                .thenReturn(Optional.of(contractDocumentId));
        when(sagaEngine.execute(eq("generate-document-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(contractDocumentId)
                .verifyComplete();

        verify(sagaEngine).execute(eq("generate-document-saga"), any(StepInputs.class));
    }

    @Test
    void doHandle_whenSagaFails_shouldEmitError() {
        UUID contractId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        GenerateContractDocumentCommand cmd = new GenerateContractDocumentCommand(contractId, templateId);

        when(sagaResult.isFailed()).thenReturn(true);
        when(sagaResult.firstErrorStepId()).thenReturn(Optional.of("generate-document"));
        when(sagaEngine.execute(eq("generate-document-saga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorMatches(e -> e.getMessage().contains("generate-document-saga failed"))
                .verify();
    }
}
