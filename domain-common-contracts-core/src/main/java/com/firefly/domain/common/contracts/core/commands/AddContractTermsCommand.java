package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to add terms to an existing contract.
 *
 * @param contractId     the identifier of the contract
 * @param termTemplateId the identifier of the term template to apply
 * @param termValue      the textual value for the term
 */
public record AddContractTermsCommand(
        UUID contractId,
        UUID termTemplateId,
        String termValue
) implements Command<UUID> {}
