package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
import com.firefly.domain.common.contracts.core.mappers.ContractCommandMapper;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@link AddContractPartyCommand} by delegating to the contract parties SDK.
 */
@CommandHandlerComponent
public class AddContractPartyHandler extends CommandHandler<AddContractPartyCommand, UUID> {

    private final ContractPartiesApi contractPartiesApi;
    private final ContractCommandMapper mapper;

    public AddContractPartyHandler(ContractPartiesApi contractPartiesApi, ContractCommandMapper mapper) {
        this.contractPartiesApi = contractPartiesApi;
        this.mapper = mapper;
    }

    @Override
    protected Mono<UUID> doHandle(AddContractPartyCommand cmd) {
        ContractPartyDTO dto = mapper.toContractPartyDto(cmd);
        return contractPartiesApi.createContractParty(cmd.contractId(), dto, UUID.randomUUID().toString())
                .mapNotNull(result -> Objects.requireNonNull(result).getContractPartyId());
    }
}
