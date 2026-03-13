package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
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
