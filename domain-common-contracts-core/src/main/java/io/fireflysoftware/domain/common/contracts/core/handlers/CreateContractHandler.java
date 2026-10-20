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
        return contractsApi.createContract(dto, UUID.randomUUID().toString())
                .mapNotNull(result -> Objects.requireNonNull(result).getContractId());
    }
}
