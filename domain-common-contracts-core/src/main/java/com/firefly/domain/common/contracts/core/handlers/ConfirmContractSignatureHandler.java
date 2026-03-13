package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
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
