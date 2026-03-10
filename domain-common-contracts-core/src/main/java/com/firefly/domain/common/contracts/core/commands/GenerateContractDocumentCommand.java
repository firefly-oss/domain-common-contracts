package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to generate a document for an existing contract.
 *
 * @param contractId the identifier of the contract
 * @param templateId the identifier of the document template to use
 */
public record GenerateContractDocumentCommand(
        UUID contractId,
        UUID templateId
) implements Command<UUID> {}
