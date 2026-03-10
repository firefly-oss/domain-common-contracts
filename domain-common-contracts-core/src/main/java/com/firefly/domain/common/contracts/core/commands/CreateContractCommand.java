package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to create a new contract.
 *
 * @param partyId      the identifier of the initiating party
 * @param contractType the type of contract to create
 * @param description  a human-readable description of the contract
 */
public record CreateContractCommand(
        UUID partyId,
        String contractType,
        String description
) implements Command<UUID> {}
