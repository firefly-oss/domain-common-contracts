/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.contracts.core.commands;

import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/**
 * Command to request a signature on a contract document.
 *
 * @param contractId    the identifier of the contract
 * @param documentId    the identifier of the document to be signed
 * @param signerPartyId the identifier of the signing party
 */
public record RequestContractSignatureCommand(
        @NotNull UUID contractId,
        @NotNull UUID documentId,
        @NotNull UUID signerPartyId
) implements Command<UUID> {}
