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


package io.fireflysoftware.domain.common.contracts.web.controllers;

import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for contract signature operations.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/signature")
@RequiredArgsConstructor
@Tag(name = "Contract Signatures", description = "Contract signature operations")
public class ContractSignatureController {

    private final ContractService contractService;

    @Operation(summary = "Request Signature", description = "Request a signature on a contract document. Returns the durable signature submission UUID used for confirmation.")
    @PostMapping("/request")
    public Mono<ResponseEntity<UUID>> requestSignature(
            @PathVariable UUID contractId,
            @Valid @RequestBody RequestContractSignatureCommand cmd) {
        return contractService.requestContractSignature(contractId, cmd.documentId(), cmd.signerPartyId())
                .map(id -> ResponseEntity.status(HttpStatus.ACCEPTED).body(id));
    }

    @Operation(summary = "Confirm Signature", description = "Confirm a previously requested contract signature")
    @PostMapping("/confirm")
    public Mono<ResponseEntity<Object>> confirmSignature(
            @PathVariable UUID contractId,
            @Valid @RequestBody ConfirmContractSignatureCommand cmd) {
        return contractService.confirmContractSignature(contractId, cmd.signatureRequestId())
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
