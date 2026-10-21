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

import io.fireflysoftware.domain.common.contracts.core.commands.CreateContractCommand;
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
        return contractService.createAgreement(cmd)
                .map(id -> ResponseEntity.status(HttpStatus.CREATED).body((Object) id));
    }

    @Operation(summary = "Change contract status", description = "Moves the agreement along the core lifecycle and compensates the status history row")
    @PostMapping("/{contractId}/status")
    public Mono<ResponseEntity<Object>> changeStatus(
            @PathVariable UUID contractId,
            @RequestBody io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand command) {
        io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand requested =
                new io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand(
                        contractId, command.status(), command.reason());
        return contractService.changeStatus(requested)
                .map(id -> ResponseEntity.ok().body((Object) id));
    }

    @Operation(summary = "Delete a draft contract", description = "Deletes the agreement only while it is still a draft")
    @DeleteMapping("/{contractId}")
    public Mono<ResponseEntity<Object>> deleteContract(@PathVariable UUID contractId) {
        return contractService.deleteContract(contractId)
                .thenReturn(ResponseEntity.noContent().build());
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
