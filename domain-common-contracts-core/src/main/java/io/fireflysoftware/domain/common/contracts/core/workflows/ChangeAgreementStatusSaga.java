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

import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.core.contract.sdk.model.FilterRequestContractStatusHistoryDTO;
import io.fireflysoftware.core.contract.sdk.model.PaginationRequest;
import io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractStatusHistoryCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.UpdateContractCommand;
import io.fireflysoftware.domain.common.contracts.core.lifecycle.AgreementLifecycle;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "ChangeAgreementStatusSaga")
@Service
public class ChangeAgreementStatusSaga {

    private final CommandBus commandBus;
    private final ContractsApi contractsApi;
    private final ContractStatusHistoryApi contractStatusHistoryApi;

    public ChangeAgreementStatusSaga(CommandBus commandBus,
                                     ContractsApi contractsApi,
                                     ContractStatusHistoryApi contractStatusHistoryApi) {
        this.commandBus = commandBus;
        this.contractsApi = contractsApi;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    @SagaStep(id = "readAgreement")
    @StepEvent(type = "contract.read")
    public Mono<ContractDTO> readAgreement(ChangeContractStatusCommand cmd, ExecutionContext ctx) {
        return contractsApi.getContractById(cmd.contractId(), UUID.randomUUID().toString())
                .doOnNext(current -> ctx.putVariable("previousAgreement", current));
    }

    @SagaStep(id = "changeAgreementStatus", dependsOn = "readAgreement", compensate = "restoreAgreementStatus")
    @StepEvent(type = "contract.statusChanged")
    public Mono<UUID> changeAgreementStatus(ChangeContractStatusCommand cmd, ExecutionContext ctx) {
        ContractDTO current = ctx.getVariableAs("previousAgreement", ContractDTO.class);
        if (current == null) {
            return Mono.error(new IllegalStateException("Contract was not found"));
        }
        ContractDTO.ContractStatusEnum from = current.getContractStatus() == null
                ? ContractDTO.ContractStatusEnum.DRAFT
                : current.getContractStatus();
        if (!AgreementLifecycle.canTransition(from, cmd.status())) {
            return Mono.error(new IllegalStateException(
                    "Contract " + cmd.contractId() + " cannot move from " + from + " to " + cmd.status()));
        }
        UpdateContractCommand update = new UpdateContractCommand();
        BeanUtils.copyProperties(current, update);
        update.setContractStatus(cmd.status());
        return commandBus.send(update)
                .flatMap(id -> newestHistoryId(cmd.contractId())
                        .doOnNext(historyId -> ctx.putVariable("statusHistoryId", historyId))
                        .thenReturn(id));
    }

    public Mono<Void> restoreAgreementStatus(UUID contractId, ExecutionContext ctx) {
        ContractDTO previous = ctx.getVariableAs("previousAgreement", ContractDTO.class);
        UUID historyId = ctx.getVariableAs("statusHistoryId", UUID.class);
        Mono<Void> deleteHistory = historyId == null
                ? Mono.empty()
                : commandBus.send(new RemoveContractStatusHistoryCommand(contractId, historyId));
        if (previous == null) {
            return deleteHistory;
        }
        UpdateContractCommand restore = new UpdateContractCommand();
        BeanUtils.copyProperties(previous, restore);
        return deleteHistory.then(commandBus.send(restore)).then();
    }

    private Mono<UUID> newestHistoryId(UUID contractId) {
        FilterRequestContractStatusHistoryDTO filter = new FilterRequestContractStatusHistoryDTO()
                .pagination(new PaginationRequest().pageNumber(0).pageSize(20));
        return contractStatusHistoryApi.filterContractStatusHistory(contractId, filter, UUID.randomUUID().toString())
                .map(page -> {
                    if (page == null || page.getContent() == null) {
                        return null;
                    }
                    return page.getContent().stream()
                            .filter(ContractStatusHistoryDTO.class::isInstance)
                            .map(ContractStatusHistoryDTO.class::cast)
                            .filter(row -> row.getStatusEndDate() == null)
                            .map(ContractStatusHistoryDTO::getContractStatusHistoryId)
                            .findFirst()
                            .orElse(null);
                });
    }
}
