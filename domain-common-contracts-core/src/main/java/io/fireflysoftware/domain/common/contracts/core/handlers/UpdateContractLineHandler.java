package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractLinesApi;
import io.fireflysoftware.domain.common.contracts.core.commands.UpdateContractLineCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@CommandHandlerComponent
public class UpdateContractLineHandler extends CommandHandler<UpdateContractLineCommand, UUID> {

    private final ContractLinesApi contractLinesApi;

    public UpdateContractLineHandler(ContractLinesApi contractLinesApi) {
        this.contractLinesApi = contractLinesApi;
    }

    @Override
    protected Mono<UUID> doHandle(UpdateContractLineCommand cmd) {
        return contractLinesApi.update(cmd.getContractId(), cmd.lineId(), cmd, UUID.randomUUID().toString())
                .mapNotNull(line -> Objects.requireNonNull(Objects.requireNonNull(line).getContractLineId()));
    }
}
