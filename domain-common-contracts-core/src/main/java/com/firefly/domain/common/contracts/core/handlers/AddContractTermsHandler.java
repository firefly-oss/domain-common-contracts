package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.core.contract.sdk.model.ContractTermDynamicDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
import com.firefly.domain.common.contracts.core.mappers.ContractCommandMapper;
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
        return contractTermsApi.createContractTerm(cmd.contractId(), dto)
                .mapNotNull(result -> Objects.requireNonNull(result).getTermId());
    }
}
