package com.firefly.domain.common.contracts.web.controllers;

import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
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
 * REST controller for contract document management operations.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/documents")
@RequiredArgsConstructor
@Tag(name = "Contract Documents", description = "Contract document management operations")
public class ContractDocumentController {

    private final ContractService contractService;

    @Operation(summary = "Generate Document", description = "Generate a document for a contract")
    @PostMapping("/generate")
    public Mono<ResponseEntity<Object>> generateDocument(
            @PathVariable UUID contractId,
            @Valid @RequestBody GenerateContractDocumentCommand cmd) {
        return contractService.generateContractDocument(contractId, cmd.templateId())
                .map(id -> ResponseEntity.status(HttpStatus.ACCEPTED).body((Object) id));
    }

    @Operation(summary = "List Documents", description = "Retrieve all documents associated with a contract")
    @GetMapping
    public Mono<ResponseEntity<Object>> listDocuments(@PathVariable UUID contractId) {
        return contractService.getContractDocuments(contractId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Get Document", description = "Retrieve a specific document associated with a contract")
    @GetMapping("/{docId}")
    public Mono<ResponseEntity<Object>> getDocument(
            @PathVariable UUID contractId,
            @PathVariable UUID docId) {
        return contractService.getContractDocument(contractId, docId)
                .map(ResponseEntity::ok);
    }
}
