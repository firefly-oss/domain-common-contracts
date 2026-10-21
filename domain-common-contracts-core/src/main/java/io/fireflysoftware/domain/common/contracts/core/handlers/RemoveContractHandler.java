package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.RemoveContractCommand;
import io.fireflysoftware.domain.common.contracts.core.lifecycle.AgreementLifecycle;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class RemoveContractHandler extends CommandHandler<RemoveContractCommand, Void> {

    private final ContractsApi contractsApi;

    public RemoveContractHandler(ContractsApi contractsApi) {
        this.contractsApi = contractsApi;
    }

    @Override
    protected Mono<Void> doHandle(RemoveContractCommand cmd) {
        return contractsApi.getContractById(cmd.contractId(), UUID.randomUUID().toString())
                .flatMap(contract -> {
                    ContractDTO.ContractStatusEnum status = contract.getContractStatus();
                    if (!AgreementLifecycle.canDelete(status)) {
                        return Mono.error(new IllegalStateException(
                                "Contract " + cmd.contractId() + " is " + status
                                        + " and cannot be deleted; terminate or cancel it so the commercial history is kept"));
                    }
                    return contractsApi.deleteContract(cmd.contractId(), UUID.randomUUID().toString());
                })
                .then();
    }
}
