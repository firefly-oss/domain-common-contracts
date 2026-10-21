package io.fireflysoftware.domain.common.contracts.core.commands;

import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

public class UpdateContractCommand extends ContractDTO implements Command<UUID> {
}
