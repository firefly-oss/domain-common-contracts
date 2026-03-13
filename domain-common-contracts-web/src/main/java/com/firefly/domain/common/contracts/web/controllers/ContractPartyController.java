package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
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
 * REST controller for contract party management operations.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/parties")
@RequiredArgsConstructor
@Tag(name = "Contract Parties", description = "Contract party management operations")
public class ContractPartyController {

    private final ContractService contractService;

    @Operation(summary = "Add Party", description = "Add a party to a contract")
    @PostMapping
    public Mono<ResponseEntity<Object>> addParty(
            @PathVariable UUID contractId,
            @Valid @RequestBody AddContractPartyCommand cmd) {
        return contractService.addContractParty(contractId, cmd.partyId(), cmd.role())
                .map(id -> ResponseEntity.status(HttpStatus.CREATED).body((Object) id));
    }

    @Operation(summary = "List Parties", description = "Retrieve all parties associated with a contract")
    @GetMapping
    public Mono<ResponseEntity<Object>> listParties(@PathVariable UUID contractId) {
        return contractService.getContractParties(contractId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Remove Party", description = "Remove a party from a contract")
    @DeleteMapping("/{partyId}")
    public Mono<ResponseEntity<Void>> removeParty(
            @PathVariable UUID contractId,
            @PathVariable UUID partyId) {
        return contractService.removeContractParty(contractId, partyId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
