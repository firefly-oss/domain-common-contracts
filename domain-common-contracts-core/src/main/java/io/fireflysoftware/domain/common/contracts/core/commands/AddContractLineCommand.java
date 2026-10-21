package io.fireflysoftware.domain.common.contracts.core.commands;

import io.fireflysoftware.core.contract.sdk.model.ContractLineDTO;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

public class AddContractLineCommand extends ContractLineDTO implements Command<UUID> {

    public AddContractLineCommand withContractId(UUID contractId) {
        this.setContractId(contractId);
        return this;
    }
}
