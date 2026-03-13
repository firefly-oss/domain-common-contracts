package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.core.services.ContractService;
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

    @Operation(summary = "Request Signature", description = "Request a signature on a contract document")
    @PostMapping("/request")
    public Mono<ResponseEntity<Object>> requestSignature(
            @PathVariable UUID contractId,
            @Valid @RequestBody RequestContractSignatureCommand cmd) {
        return contractService.requestContractSignature(contractId, cmd.documentId(), cmd.signerPartyId())
                .map(id -> ResponseEntity.status(HttpStatus.ACCEPTED).body((Object) id));
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
