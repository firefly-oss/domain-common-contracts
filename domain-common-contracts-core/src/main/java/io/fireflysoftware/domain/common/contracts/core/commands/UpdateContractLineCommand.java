package io.fireflysoftware.domain.common.contracts.core.commands;

import io.fireflysoftware.core.contract.sdk.model.ContractLineDTO;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

public class UpdateContractLineCommand extends ContractLineDTO implements Command<UUID> {

    private UUID lineId;

    public UpdateContractLineCommand withContractId(UUID contractId) {
        this.setContractId(contractId);
        return this;
    }

    public UpdateContractLineCommand withContractLineId(UUID contractLineId) {
        this.lineId = contractLineId;
        return this;
    }

    public UUID lineId() {
        return lineId;
    }
}
