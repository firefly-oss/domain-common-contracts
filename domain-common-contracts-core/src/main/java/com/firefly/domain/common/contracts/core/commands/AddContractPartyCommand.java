package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to add a party to an existing contract.
 *
 * @param contractId the identifier of the contract
 * @param partyId    the identifier of the party to add
 * @param role       the role of the party in the contract
 */
public record AddContractPartyCommand(
        UUID contractId,
        UUID partyId,
        String role
) implements Command<UUID> {}
