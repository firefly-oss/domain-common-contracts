package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to confirm a previously requested contract signature.
 *
 * @param contractId         the identifier of the contract
 * @param signatureRequestId the identifier of the signature request to confirm
 */
public record ConfirmContractSignatureCommand(
        UUID contractId,
        String signatureRequestId
) implements Command<Void> {}
