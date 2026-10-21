package io.fireflysoftware.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

public record RemoveContractStatusHistoryCommand(UUID contractId, UUID contractStatusHistoryId) implements Command<Void> {
}
