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

import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
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

    @Operation(summary = "Legacy document generation requires migration", deprecated = true,
            description = "Always returns 409. Use the governed document route with a complete exact intent.")
    @ApiResponse(responseCode="200", description="Historical schema retained for client compatibility; current calls return409.",
            content=@Content(mediaType="*/*",schema=@Schema(type="object",implementation=Object.class)))
    @ApiResponse(responseCode = "409", description = "GOVERNED_DOCUMENT_INTENT_REQUIRED")
    @DisableIdempotency
    @PostMapping("/generate")
    public Mono<ResponseEntity<Object>> generateDocument(
            @PathVariable UUID contractId,
            @Valid @RequestBody GenerateContractDocumentCommand cmd,
            @Parameter(description = "Compatibility header; this route does not cache responses or derive authority from it.")
            @RequestHeader(name = "X-Idempotency-Key", required = false) String ignoredIdempotencyKey) {
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
