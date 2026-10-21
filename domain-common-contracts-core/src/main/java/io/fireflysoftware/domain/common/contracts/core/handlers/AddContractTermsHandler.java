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

import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractTermDynamicDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractTermsCommand;
import io.fireflysoftware.domain.common.contracts.core.mappers.ContractCommandMapper;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@link AddContractTermsCommand} by delegating to the contract terms SDK.
 */
@CommandHandlerComponent
public class AddContractTermsHandler extends CommandHandler<AddContractTermsCommand, UUID> {

    private final ContractTermsApi contractTermsApi;
    private final ContractCommandMapper mapper;

    public AddContractTermsHandler(ContractTermsApi contractTermsApi, ContractCommandMapper mapper) {
        this.contractTermsApi = contractTermsApi;
        this.mapper = mapper;
    }

    @Override
    protected Mono<UUID> doHandle(AddContractTermsCommand cmd) {
        ContractTermDynamicDTO dto = mapper.toContractTermDto(cmd);
        return contractTermsApi.createContractTerm(cmd.contractId(), dto, UUID.randomUUID().toString())
                .mapNotNull(result -> Objects.requireNonNull(result).getTermId());
    }
}
