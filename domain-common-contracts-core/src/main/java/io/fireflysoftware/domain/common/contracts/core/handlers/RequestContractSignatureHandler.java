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

import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Handles {@link RequestContractSignatureCommand} by executing the
 * {@code request-signature-saga}, which creates a signature record, submits it
 * to the e-signature provider, and records the SUBMITTED_FOR_APPROVAL status.
 * Returns the resulting {@code contractStatusHistoryId}.
 */
@CommandHandlerComponent
public class RequestContractSignatureHandler extends CommandHandler<RequestContractSignatureCommand, UUID> {

    private final SagaEngine sagaEngine;

    public RequestContractSignatureHandler(SagaEngine sagaEngine) {
        this.sagaEngine = sagaEngine;
    }

    @Override
    protected Mono<UUID> doHandle(RequestContractSignatureCommand cmd) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("create-signature-request", cmd)
                .forStepId("send-to-provider", cmd)
                .forStepId("update-contract-status", cmd)
                .build();

        return sagaEngine.execute("request-signature-saga", inputs)
                .flatMap(result -> {
                    if (result.isFailed()) {
                        return Mono.error(new RuntimeException(
                                "request-signature-saga failed at step: " +
                                result.firstErrorStepId().orElse("unknown")));
                    }
                    return Mono.justOrEmpty(
                            result.resultOf("update-contract-status", UUID.class).orElse(null));
                });
    }
}
