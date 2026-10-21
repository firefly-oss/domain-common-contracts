package io.fireflysoftware.domain.common.contracts.core.commands;

import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Saga input for a status change. The saga reads the agreement and writes the new status.
 */
public record ChangeContractStatusCommand(UUID contractId, ContractDTO.ContractStatusEnum status, String reason)
        implements Command<UUID> {
}
