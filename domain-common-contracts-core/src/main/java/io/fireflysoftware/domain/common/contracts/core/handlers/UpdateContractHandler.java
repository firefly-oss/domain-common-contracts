package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.UpdateContractCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@CommandHandlerComponent
public class UpdateContractHandler extends CommandHandler<UpdateContractCommand, UUID> {

    private final ContractsApi contractsApi;

    public UpdateContractHandler(ContractsApi contractsApi) {
        this.contractsApi = contractsApi;
    }

    @Override
    protected Mono<UUID> doHandle(UpdateContractCommand cmd) {
        return contractsApi.updateContract(cmd.getContractId(), cmd, UUID.randomUUID().toString())
                .mapNotNull(dto -> Objects.requireNonNull(Objects.requireNonNull(dto).getContractId()));
    }
}
