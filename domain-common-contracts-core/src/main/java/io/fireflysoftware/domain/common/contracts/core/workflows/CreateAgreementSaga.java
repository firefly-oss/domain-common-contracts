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


package io.fireflysoftware.domain.common.contracts.core.workflows;

import io.fireflysoftware.core.contract.sdk.model.ContractLineDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractLineCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.CreateContractCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractLineCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "CreateAgreementSaga")
@Service
public class CreateAgreementSaga {

    private static final UUID SKIPPED = new UUID(0L, 0L);

    private final CommandBus commandBus;

    public CreateAgreementSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "createContract", compensate = "removeContract")
    @StepEvent(type = "contract.created")
    public Mono<UUID> createContract(CreateContractCommand cmd, ExecutionContext ctx) {
        return commandBus.send(cmd)
                .doOnNext(contractId -> ctx.putVariable("contractId", contractId));
    }

    public Mono<Void> removeContract(UUID contractId) {
        if (contractId == null) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveContractCommand(contractId));
    }

    @SagaStep(id = "addContractLine", dependsOn = "createContract", compensate = "removeContractLine")
    @StepEvent(type = "contractLine.created")
    public Mono<UUID> addContractLine(ContractLineDTO line, ExecutionContext ctx) {
        if (line == null) {
            return Mono.just(SKIPPED);
        }
        AddContractLineCommand command = new AddContractLineCommand();
        BeanUtils.copyProperties(line, command);
        command.setContractId(ctx.getVariableAs("contractId", UUID.class));
        if (command.getIsPrimary() == null && ctx.getVariable("primaryLineClaimed") == null) {
            command.setIsPrimary(Boolean.TRUE);
            ctx.putVariable("primaryLineClaimed", Boolean.TRUE);
        }
        return commandBus.send(command);
    }

    public Mono<Void> removeContractLine(UUID contractLineId, ExecutionContext ctx) {
        if (contractLineId == null || SKIPPED.equals(contractLineId)) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveContractLineCommand(
                ctx.getVariableAs("contractId", UUID.class), contractLineId));
    }
}
