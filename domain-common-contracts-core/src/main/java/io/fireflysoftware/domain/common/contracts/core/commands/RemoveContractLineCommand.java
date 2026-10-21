package io.fireflysoftware.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

public record RemoveContractLineCommand(UUID contractId, UUID contractLineId) implements Command<Void> {
}
