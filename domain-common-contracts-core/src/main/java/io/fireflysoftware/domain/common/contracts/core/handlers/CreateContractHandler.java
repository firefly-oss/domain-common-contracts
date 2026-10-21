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

import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.CreateContractCommand;
import io.fireflysoftware.domain.common.contracts.core.mappers.ContractCommandMapper;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@link CreateContractCommand} by delegating to the contract management SDK.
 */
@CommandHandlerComponent
public class CreateContractHandler extends CommandHandler<CreateContractCommand, UUID> {

    private final ContractsApi contractsApi;
    private final ContractCommandMapper mapper;

    public CreateContractHandler(ContractsApi contractsApi, ContractCommandMapper mapper) {
        this.contractsApi = contractsApi;
        this.mapper = mapper;
    }

    @Override
    protected Mono<UUID> doHandle(CreateContractCommand cmd) {
        ContractDTO dto = mapper.toContractDto(cmd);
        if (dto.getContractStatus() == null) {
            dto.setContractStatus(ContractDTO.ContractStatusEnum.DRAFT);
        }
        if (cmd.getCurrencyCode() != null) {
            dto.setCurrencyCode(cmd.getCurrencyCode());
        }
        if (cmd.getProductId() != null) {
            dto.setProductId(cmd.getProductId());
        }
        if (cmd.getPredecessorContractId() != null) {
            dto.setPredecessorContractId(cmd.getPredecessorContractId());
        }
        if (cmd.getLinkKind() != null) {
            dto.setLinkKind(cmd.getLinkKind());
        }
        return contractsApi.createContract(dto, UUID.randomUUID().toString())
                .mapNotNull(result -> Objects.requireNonNull(result).getContractId());
    }
}
