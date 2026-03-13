package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
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
 * REST controller for contract terms management operations.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/terms")
@RequiredArgsConstructor
@Tag(name = "Contract Terms", description = "Contract terms management operations")
public class ContractTermsController {

    private final ContractService contractService;

    @Operation(summary = "Add Terms", description = "Add terms to a contract")
    @PostMapping
    public Mono<ResponseEntity<Object>> addTerms(
            @PathVariable UUID contractId,
            @Valid @RequestBody AddContractTermsCommand cmd) {
        return contractService.addContractTerms(contractId, cmd.termTemplateId(), cmd.termValue())
                .map(id -> ResponseEntity.status(HttpStatus.CREATED).body((Object) id));
    }

    @Operation(summary = "List Terms", description = "Retrieve all terms associated with a contract")
    @GetMapping
    public Mono<ResponseEntity<Object>> listTerms(@PathVariable UUID contractId) {
        return contractService.getContractTerms(contractId)
                .map(ResponseEntity::ok);
    }
}
