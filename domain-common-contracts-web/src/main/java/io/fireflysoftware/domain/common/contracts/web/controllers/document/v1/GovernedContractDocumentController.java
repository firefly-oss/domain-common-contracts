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
package io.fireflysoftware.domain.common.contracts.web.controllers.document.v1;

import io.fireflysoftware.core.contract.sdk.model.ContractDocumentOperation;
import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedContractDocumentCoordinator;
import io.fireflysoftware.domain.common.contracts.interfaces.dtos.document.v1.GovernedContractDocumentIntent;
import io.fireflysoftware.domain.common.contracts.web.documents.v1.GovernedContractDocumentInputs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** Uses the native contract journal as the sole durable issuance and attachment cursor. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/contracts/{contractId}/governed-documents")
@Tag(
    name = "Governed Contract Documents",
    description = "Exact CONTRACT_DOCUMENT_V1 issuance and current-authority recovery")
public class GovernedContractDocumentController {
  private final GovernedContractDocumentCoordinator coordinator;

  public GovernedContractDocumentController(GovernedContractDocumentCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @DisableIdempotency
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "issueGovernedContractDocument",
      summary = "Issue and attach an exact governed contract action")
  @ApiResponse(
      responseCode = "200",
      description =
          "Current retained journal state; issued evidence is never deleted as compensation")
  public Mono<ResponseEntity<ContractDocumentOperation>> issueGovernedContractDocument(
      @PathVariable UUID tenantId,
      @PathVariable UUID contractId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(schema = @Schema(implementation = GovernedContractDocumentIntent.class)))
          @RequestBody
          String body) {
    return Mono.fromCallable(() -> GovernedContractDocumentInputs.read(body))
        .flatMap(intent -> coordinator.issue(tenantId, contractId, intent))
        .map(GovernedContractDocumentController::current);
  }

  @DisableIdempotency
  @PostMapping("/{actionId}/resume")
  @Operation(
      operationId = "resumeGovernedContractDocument",
      summary = "Resume the exact retained action under current authority")
  public Mono<ResponseEntity<ContractDocumentOperation>> resumeGovernedContractDocument(
      @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId) {
    return coordinator
        .resume(tenantId, contractId, actionId)
        .map(GovernedContractDocumentController::current);
  }

  @DisableIdempotency
  @GetMapping("/{actionId}")
  @Operation(
      operationId = "getGovernedContractDocument",
      summary = "Read retained governed action metadata under current authority")
  public Mono<ResponseEntity<ContractDocumentOperation>> getGovernedContractDocument(
      @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId) {
    return coordinator
        .read(tenantId, contractId, actionId)
        .map(GovernedContractDocumentController::current);
  }

  @DisableIdempotency
  @GetMapping(value = "/{actionId}/content", produces = "text/html")
  @Operation(
      operationId = "getGovernedContractDocumentContent",
      summary = "Read verified immutable UTF-8 content of an attached action")
  @ApiResponse(
      responseCode = "200",
      description = "Exact verified bytes; current content permission is required",
      content =
          @Content(mediaType = "text/html", schema = @Schema(type = "string", format = "binary")))
  public Mono<ResponseEntity<byte[]>> getGovernedContractDocumentContent(
      @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId) {
    return coordinator
        .content(tenantId, contractId, actionId)
        .map(
            content -> {
              byte[] bytes = content.getContentUtf8().getBytes(StandardCharsets.UTF_8);
              return ResponseEntity.ok()
                  .cacheControl(CacheControl.noStore())
                  .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                  .contentLength(bytes.length)
                  .eTag(content.getContentDigest())
                  .header("X-Document-Version-Id", content.getDocumentVersionId().toString())
                  .header(
                      "Content-Disposition",
                      ContentDisposition.attachment()
                          .filename("contract-document-" + actionId + ".html")
                          .build()
                          .toString())
                  .header(
                      "Content-Security-Policy",
                      "sandbox; default-src 'none'; base-uri 'none'; form-action 'none'")
                  .header("X-Content-Type-Options", "nosniff")
                  .body(bytes);
            });
  }

  private static ResponseEntity<ContractDocumentOperation> current(
      ContractDocumentOperation value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
