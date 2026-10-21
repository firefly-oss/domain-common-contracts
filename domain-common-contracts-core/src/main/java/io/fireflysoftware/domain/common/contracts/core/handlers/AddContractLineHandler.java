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

import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractLineCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@CommandHandlerComponent
public class AddContractLineHandler extends CommandHandler<AddContractLineCommand, UUID> {

    private final ContractLinesApi contractLinesApi;

    public AddContractLineHandler(ContractLinesApi contractLinesApi) {
        this.contractLinesApi = contractLinesApi;
    }

    @Override
    protected Mono<UUID> doHandle(AddContractLineCommand cmd) {
        return contractLinesApi.create(cmd.getContractId(), cmd, UUID.randomUUID().toString())
                .mapNotNull(line -> Objects.requireNonNull(Objects.requireNonNull(line).getContractLineId()));
    }
}
