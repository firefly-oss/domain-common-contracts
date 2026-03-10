package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to remove a party from an existing contract.
 *
 * @param contractId the identifier of the contract
 * @param partyId    the identifier of the party to remove
 */
public record RemoveContractPartyCommand(
        UUID contractId,
        UUID partyId
) implements Command<Void> {}
