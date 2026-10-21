package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractLineCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class RemoveContractLineHandler extends CommandHandler<RemoveContractLineCommand, Void> {

    private final ContractLinesApi contractLinesApi;

    public RemoveContractLineHandler(ContractLinesApi contractLinesApi) {
        this.contractLinesApi = contractLinesApi;
    }

    @Override
    protected Mono<Void> doHandle(RemoveContractLineCommand cmd) {
        return contractLinesApi.delete(cmd.contractId(), cmd.contractLineId(), UUID.randomUUID().toString()).then();
    }
}
