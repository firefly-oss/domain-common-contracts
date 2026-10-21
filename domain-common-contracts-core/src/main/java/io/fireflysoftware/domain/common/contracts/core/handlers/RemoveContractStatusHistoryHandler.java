package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractStatusHistoryCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class RemoveContractStatusHistoryHandler extends CommandHandler<RemoveContractStatusHistoryCommand, Void> {

    private final ContractStatusHistoryApi contractStatusHistoryApi;

    public RemoveContractStatusHistoryHandler(ContractStatusHistoryApi contractStatusHistoryApi) {
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    @Override
    protected Mono<Void> doHandle(RemoveContractStatusHistoryCommand cmd) {
        return contractStatusHistoryApi
                .deleteContractStatusHistory(cmd.contractId(), cmd.contractStatusHistoryId(), UUID.randomUUID().toString())
                .then();
    }
}
