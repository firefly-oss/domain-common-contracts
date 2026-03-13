package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Handles {@link GenerateContractDocumentCommand} by executing the
 * {@code generate-document-saga}, which atomically creates the document in ECM
 * and links it to the contract. Returns the resulting {@code contractDocumentId}.
 */
@CommandHandlerComponent
public class GenerateContractDocumentHandler extends CommandHandler<GenerateContractDocumentCommand, UUID> {

    private final SagaEngine sagaEngine;

    public GenerateContractDocumentHandler(SagaEngine sagaEngine) {
        this.sagaEngine = sagaEngine;
    }

    @Override
    protected Mono<UUID> doHandle(GenerateContractDocumentCommand cmd) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("generate-document", cmd)
                .forStepId("attach-to-contract", cmd)
                .build();

        return sagaEngine.execute("generate-document-saga", inputs)
                .flatMap(result -> {
                    if (result.isFailed()) {
                        return Mono.error(new RuntimeException(
                                "generate-document-saga failed at step: " +
                                result.firstErrorStepId().orElse("unknown")));
                    }
                    return Mono.justOrEmpty(
                            result.resultOf("attach-to-contract", UUID.class).orElse(null));
                });
    }
}
