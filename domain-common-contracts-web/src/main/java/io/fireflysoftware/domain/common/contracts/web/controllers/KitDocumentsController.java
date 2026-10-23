/*
 * Copyright 2026 Firefly Software Foundation
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

import io.fireflysoftware.domain.common.contracts.core.documents.KitDocumentService;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kit-documents")
public class KitDocumentsController {

    private final KitDocumentService documents;

    public KitDocumentsController(KitDocumentService documents) {
        this.documents = documents;
    }

    @Operation(summary = "Legacy document issue requires migration", deprecated = true,
            description = "Always returns 409. Use a governed document intent with an actual contract and action identity.")
    @ApiResponse(responseCode="200", description="Historical schema retained for client compatibility; current calls return409.",
            content=@Content(mediaType="*/*",schema=@Schema(implementation=RecordedId.class)))
    @ApiResponse(responseCode = "409", description = "GOVERNED_DOCUMENT_INTENT_REQUIRED")
    @DisableIdempotency
    @PostMapping
    public Mono<RecordedId> issue(@RequestBody IssueBody body,
            @Parameter(description = "Compatibility header; this route does not cache responses or derive authority from it.")
            @RequestHeader(name = "X-Idempotency-Key", required = false) String ignoredIdempotencyKey) {
        return documents.issue(body.templateCode(), body.partyId(), body.contractId()).map(RecordedId::new);
    }

    @Operation(summary = "Legacy document read requires migration", deprecated = true,
            description = "Always returns 409. Read content through the governed contract action.")
    @ApiResponse(responseCode="200", description="Historical schema retained for client compatibility; current calls return409.",
            content=@Content(mediaType="*/*",schema=@Schema(implementation=IssuedView.class)))
    @ApiResponse(responseCode = "409", description = "GOVERNED_DOCUMENT_INTENT_REQUIRED")
    @DisableIdempotency
    @GetMapping("/{id}")
    public Mono<IssuedView> read(@PathVariable UUID id,
            @Parameter(description = "Compatibility header; this route does not cache responses or derive authority from it.")
            @RequestHeader(name = "X-Idempotency-Key", required = false) String ignoredIdempotencyKey) {
        return documents.read(id).map(document -> new IssuedView(
                document.id(), document.templateCode(), document.name()));
    }

    public record IssueBody(String templateCode, UUID partyId, UUID contractId) {
    }

    public record RecordedId(UUID id) {
    }

    public record IssuedView(UUID id, String templateCode, String name) {
    }
}
