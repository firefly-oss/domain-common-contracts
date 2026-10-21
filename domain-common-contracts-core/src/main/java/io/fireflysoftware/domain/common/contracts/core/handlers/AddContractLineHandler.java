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
