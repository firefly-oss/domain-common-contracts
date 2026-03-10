package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractPartiesApi;
import com.firefly.domain.common.contracts.core.commands.RemoveContractPartyCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link RemoveContractPartyCommand} by delegating to the contract parties SDK.
 */
@CommandHandlerComponent
public class RemoveContractPartyHandler extends CommandHandler<RemoveContractPartyCommand, Void> {

    private final ContractPartiesApi contractPartiesApi;

    public RemoveContractPartyHandler(ContractPartiesApi contractPartiesApi) {
        this.contractPartiesApi = contractPartiesApi;
    }

    @Override
    protected Mono<Void> doHandle(RemoveContractPartyCommand cmd) {
        return contractPartiesApi.deleteContractParty(cmd.contractId(), cmd.partyId());
    }
}
