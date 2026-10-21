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

import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import reactor.core.publisher.Mono;

/**
 * Handles {@link ConfirmContractSignatureCommand} by executing the
 * {@code confirm-signature-saga}, which verifies the signature with the provider,
 * records the APPROVED status, and sends a notification (best-effort).
 */
@CommandHandlerComponent
public class ConfirmContractSignatureHandler extends CommandHandler<ConfirmContractSignatureCommand, Void> {

    private final SagaEngine sagaEngine;

    public ConfirmContractSignatureHandler(SagaEngine sagaEngine) {
        this.sagaEngine = sagaEngine;
    }

    @Override
    protected Mono<Void> doHandle(ConfirmContractSignatureCommand cmd) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("verify-signature", cmd)
                .forStepId("update-contract-status", cmd)
                .forStepId("send-notification", cmd)
                .build();

        return sagaEngine.execute("confirm-signature-saga", inputs)
                .flatMap(result -> {
                    if (result.isFailed()) {
                        return Mono.error(new RuntimeException(
                                "confirm-signature-saga failed at step: " +
                                result.firstErrorStepId().orElse("unknown")));
                    }
                    return Mono.empty();
                });
    }
}
