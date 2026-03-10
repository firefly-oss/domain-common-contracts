package com.firefly.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command to request a signature on a contract document.
 *
 * @param contractId    the identifier of the contract
 * @param documentId    the identifier of the document to be signed
 * @param signerPartyId the identifier of the signing party
 */
public record RequestContractSignatureCommand(
        UUID contractId,
        UUID documentId,
        UUID signerPartyId
) implements Command<UUID> {}
