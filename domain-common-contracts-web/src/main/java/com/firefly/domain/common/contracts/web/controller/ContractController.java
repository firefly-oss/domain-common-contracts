package com.firefly.domain.common.contracts.web.controller;

import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
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
 * REST controller for contract management operations.
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Contract management operations")
public class ContractController {

    private final ContractService contractService;

    @Operation(summary = "Create Contract", description = "Create a new contract")
    @PostMapping
    public Mono<ResponseEntity<Object>> createContract(@Valid @RequestBody CreateContractCommand cmd) {
        return contractService.createContract(cmd.partyId(), cmd.contractType(), cmd.description())
                .map(id -> ResponseEntity.status(HttpStatus.CREATED).body((Object) id));
    }

    @Operation(summary = "Get Contract Detail", description = "Retrieve the details of a specific contract")
    @GetMapping("/{contractId}")
    public Mono<ResponseEntity<Object>> getContractDetail(@PathVariable UUID contractId) {
        return contractService.getContractDetail(contractId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "List Contracts by Party", description = "Retrieve all contracts associated with a party")
    @GetMapping("/by-party/{partyId}")
    public Mono<ResponseEntity<Object>> listByParty(@PathVariable UUID partyId) {
        return contractService.getContractsByParty(partyId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "List Active Contracts by Party", description = "Retrieve all active contracts associated with a party")
    @GetMapping("/by-party/{partyId}/active")
    public Mono<ResponseEntity<Object>> listActiveByParty(@PathVariable UUID partyId) {
        return contractService.getActiveContractsByParty(partyId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Get Contract Status", description = "Retrieve the current status of a contract")
    @GetMapping("/{contractId}/status")
    public Mono<ResponseEntity<Object>> getStatus(@PathVariable UUID contractId) {
        return contractService.getContractStatus(contractId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Get Contract Status History", description = "Retrieve the full status history of a contract")
    @GetMapping("/{contractId}/status-history")
    public Mono<ResponseEntity<Object>> getStatusHistory(@PathVariable UUID contractId) {
        return contractService.getContractStatusHistory(contractId)
                .map(ResponseEntity::ok);
    }
}
