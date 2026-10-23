# Contract Orchestration: source reference

[Repository guide](../../README.md) · [Machine-readable inventory](source-inventory.json)

This reference is derived from checked-in source. It separates declared HTTP mappings, typed contracts, property placeholders, and validation locations. Java reflection, inherited framework routes, generated targets, and runtime infrastructure are outside this static inventory.

## HTTP operations

| Mapping | Method | Controller | Kind |
| --- | --- | --- | --- |
| `GET /api/v1/contracts/{contractId}` | `public Mono<ResponseEntity<Object>> getContractDetail(@PathVariable UUID contractId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `GET /api/v1/contracts/by-party/{partyId}` | `public Mono<ResponseEntity<Object>> listByParty(@PathVariable UUID partyId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `GET /api/v1/contracts/by-party/{partyId}/active` | `public Mono<ResponseEntity<Object>> listActiveByParty(@PathVariable UUID partyId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/status` | `public Mono<ResponseEntity<Object>> getStatus(@PathVariable UUID contractId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/status-history` | `public Mono<ResponseEntity<Object>> getStatusHistory(@PathVariable UUID contractId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `POST /api/v1/contracts` | `public Mono<ResponseEntity<Object>> createContract(@Valid @RequestBody CreateContractCommand cmd)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/status` | `public Mono<ResponseEntity<Object>> changeStatus( @PathVariable UUID contractId, @RequestBody io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand command)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `DELETE /api/v1/contracts/{contractId}` | `public Mono<ResponseEntity<Object>> deleteContract(@PathVariable UUID contractId)` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/documents` | `public Mono<ResponseEntity<Object>> listDocuments(@PathVariable UUID contractId)` | [ContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/documents/{docId}` | `public Mono<ResponseEntity<Object>> getDocument( @PathVariable UUID contractId, @PathVariable UUID docId)` | [ContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/documents/generate` | `public Mono<ResponseEntity<Object>> generateDocument( @PathVariable UUID contractId, @Valid @RequestBody GenerateContractDocumentCommand cmd, @Parameter(description = "Compatibility header; this route does not cache responses or derive authority from it.") @RequestHeader(name = "X-Idempotency-Key", required = false) String ignoredIdempotencyKey)` | [ContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/parties` | `public Mono<ResponseEntity<Object>> listParties(@PathVariable UUID contractId)` | [ContractPartyController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/parties` | `public Mono<ResponseEntity<Object>> addParty( @PathVariable UUID contractId, @Valid @RequestBody AddContractPartyCommand cmd)` | [ContractPartyController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyController.java) | Local declaration |
| `DELETE /api/v1/contracts/{contractId}/parties/{partyId}` | `public Mono<ResponseEntity<Void>> removeParty( @PathVariable UUID contractId, @PathVariable UUID partyId)` | [ContractPartyController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/signature/request` | `public Mono<ResponseEntity<UUID>> requestSignature( @PathVariable UUID contractId, @Valid @RequestBody RequestContractSignatureCommand cmd)` | [ContractSignatureController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractSignatureController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/signature/confirm` | `public Mono<ResponseEntity<Object>> confirmSignature( @PathVariable UUID contractId, @Valid @RequestBody ConfirmContractSignatureCommand cmd)` | [ContractSignatureController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractSignatureController.java) | Local declaration |
| `GET /api/v1/contracts/{contractId}/terms` | `public Mono<ResponseEntity<Object>> listTerms(@PathVariable UUID contractId)` | [ContractTermsController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractTermsController.java) | Local declaration |
| `POST /api/v1/contracts/{contractId}/terms` | `public Mono<ResponseEntity<Object>> addTerms( @PathVariable UUID contractId, @Valid @RequestBody AddContractTermsCommand cmd)` | [ContractTermsController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractTermsController.java) | Local declaration |
| `GET /api/v1/kit-documents/{id}` | `public Mono<IssuedView> read(@PathVariable UUID id, @Parameter(description = "Compatibility header; this route does not cache responses or derive authority from it.") @RequestHeader(name = "X-Idempotency-Key", required = false) String ignoredIdempotencyKey)` | [KitDocumentsController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/KitDocumentsController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/contracts/{contractId}/governed-documents/{actionId}` | `public Mono<ResponseEntity<ContractDocumentOperation>> getGovernedContractDocument( @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId)` | [GovernedContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/document/v1/GovernedContractDocumentController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/contracts/{contractId}/governed-documents/{actionId}/content` | `public Mono<ResponseEntity<byte[]>> getGovernedContractDocumentContent( @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId)` | [GovernedContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/document/v1/GovernedContractDocumentController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/contracts/{contractId}/governed-documents` | `public Mono<ResponseEntity<ContractDocumentOperation>> issueGovernedContractDocument( @PathVariable UUID tenantId, @PathVariable UUID contractId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content(schema = @Schema(implementation = GovernedContractDocumentIntent.class))) @RequestBody String body)` | [GovernedContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/document/v1/GovernedContractDocumentController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/contracts/{contractId}/governed-documents/{actionId}/resume` | `public Mono<ResponseEntity<ContractDocumentOperation>> resumeGovernedContractDocument( @PathVariable UUID tenantId, @PathVariable UUID contractId, @PathVariable UUID actionId)` | [GovernedContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/document/v1/GovernedContractDocumentController.java) | Local declaration |

## Data contracts

### AddContractPartyCommand

[AddContractPartyCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/AddContractPartyCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `partyId` | `UUID` | See declaration / service rules |
| `role` | `String` | See declaration / service rules |

### AddContractTermsCommand

[AddContractTermsCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/AddContractTermsCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `termTemplateId` | `UUID` | See declaration / service rules |
| `termValue` | `String` | See declaration / service rules |

### ChangeContractStatusCommand

[ChangeContractStatusCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/ChangeContractStatusCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `status` | `ContractDTO.ContractStatusEnum` | See declaration / service rules |
| `reason` | `String` | See declaration / service rules |

### ConfirmContractSignatureCommand

[ConfirmContractSignatureCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/ConfirmContractSignatureCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `signatureRequestId` | `String` | See declaration / service rules |

Validation annotations present in this declaration: `@NotBlank`, `@NotNull`. Inspect the linked source for the field-level constraints.

### GenerateContractDocumentCommand

[GenerateContractDocumentCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/GenerateContractDocumentCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `templateId` | `UUID` | See declaration / service rules |

### RemoveContractCommand

[RemoveContractCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/RemoveContractCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |

### RemoveContractLineCommand

[RemoveContractLineCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/RemoveContractLineCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `contractLineId` | `UUID` | See declaration / service rules |

### RemoveContractPartyCommand

[RemoveContractPartyCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/RemoveContractPartyCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `partyId` | `UUID` | See declaration / service rules |

### RemoveContractStatusHistoryCommand

[RemoveContractStatusHistoryCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/RemoveContractStatusHistoryCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `contractStatusHistoryId` | `UUID` | See declaration / service rules |

### RequestContractSignatureCommand

[RequestContractSignatureCommand](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/commands/RequestContractSignatureCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `documentId` | `UUID` | See declaration / service rules |
| `signerPartyId` | `UUID` | See declaration / service rules |

Validation annotations present in this declaration: `@NotNull`. Inspect the linked source for the field-level constraints.

### RequestContractSignatureHandler

[RequestContractSignatureHandler](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/handlers/RequestContractSignatureHandler.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `sagaEngine` | `SagaEngine` | See declaration / service rules |

### RequestSignatureSaga

[RequestSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSaga.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `eSignaturePort` | `ESignaturePort` | See declaration / service rules |
| `contractStatusHistoryApi` | `ContractStatusHistoryApi` | See declaration / service rules |

### GovernedPeerEndpoint

[GovernedPeerEndpoint](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedPeerEndpoint.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `baseUrl` | `String` | See declaration / service rules |
| `allowLoopbackHttp` | `boolean` | See declaration / service rules |

### SignatureRequest

[SignatureRequest](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/ports/model/SignatureRequest.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `contractId` | `UUID` | See declaration / service rules |
| `documentId` | `UUID` | See declaration / service rules |
| `signerPartyId` | `UUID` | See declaration / service rules |

### SignatureRequestResult

[SignatureRequestResult](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/ports/model/SignatureRequestResult.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `signatureRequestId` | `String` | See declaration / service rules |
| `provider` | `String` | See declaration / service rules |
| `status` | `String` | See declaration / service rules |

### GovernedContractDocumentIntent

[GovernedContractDocumentIntent](../../domain-common-contracts-interfaces/src/main/java/io/fireflysoftware/domain/common/contracts/interfaces/dtos/document/v1/GovernedContractDocumentIntent.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `actionId` | `String` | See declaration / service rules |
| `expectedContractRevision` | `Long` | See declaration / service rules |
| `selection` | `GovernedSelection` | See declaration / service rules |
| `expected` | `GovernedPins` | See declaration / service rules |
| `contractDocumentTypeId` | `String` | See declaration / service rules |
| `productVersionReferences` | `List<GovernedProductVersion>` | See declaration / service rules |
| `retention` | `GovernedRetention` | See declaration / service rules |
| `displayName` | `String` | See declaration / service rules |
| `predecessorOperationId` | `String` | See declaration / service rules |

## Ports and service contracts

### ContractService

[ContractService](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/services/ContractService.java)

```java
Mono<UUID> createContract(UUID partyId, String contractType, String description);
Mono<UUID> createAgreement(io.fireflysoftware.domain.common.contracts.core.commands.CreateContractCommand command);
Mono<UUID> changeStatus(io.fireflysoftware.domain.common.contracts.core.commands.ChangeContractStatusCommand command);
Mono<Void> deleteContract(UUID contractId);
Mono<Object> getContractDetail(UUID contractId);
Mono<UUID> addContractParty(UUID contractId, UUID partyId, String role);
Mono<Void> removeContractParty(UUID contractId, UUID partyId);
Mono<List<Object>> getContractParties(UUID contractId);
Mono<UUID> addContractTerms(UUID contractId, UUID termTemplateId, String termValue);
Mono<List<Object>> getContractTerms(UUID contractId);
Mono<List<Object>> getContractsByParty(UUID partyId);
Mono<List<Object>> getActiveContractsByParty(UUID partyId);
Mono<Object> getContractStatus(UUID contractId);
Mono<List<Object>> getContractStatusHistory(UUID contractId);
Mono<UUID> generateContractDocument(UUID contractId, UUID templateId);
Mono<List<Object>> getContractDocuments(UUID contractId);
Mono<Object> getContractDocument(UUID contractId, UUID documentId);
Mono<UUID> requestContractSignature(UUID contractId, UUID documentId, UUID signerPartyId);
Mono<Void> confirmContractSignature(UUID contractId, String signatureRequestId);
```

### ContractDocumentSourcePort

[ContractDocumentSourcePort](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourcePort.java)

```java
Mono<ContractDocumentPartyObservation> party(UUID tenant, UUID contract, UUID action, UUID party);
Mono<ContractDocumentProductObservation> product( UUID tenant, UUID contract, UUID action, UUID product, UUID version);
Mono<ContractDocumentRoleObservation> role(UUID tenant, UUID contract, UUID action, UUID role);
Mono<ContractDocumentTypeObservation> documentType( UUID tenant, UUID contract, UUID action, UUID type);
```

### GovernedDocumentCredentialPort

[GovernedDocumentCredentialPort](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentCredentialPort.java)

```java
Mono<Credential> current(Peer peer, UUID tenant, UUID contract, UUID action);
public Credential(String bearer, Instant expiresAt);
String bearer();
Instant expiresAt();
String toString();
```

### GovernedDocumentPeerPort

[GovernedDocumentPeerPort](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentPeerPort.java)

```java
Mono<ContractDocumentOperation> prepare( UUID tenant, UUID contract, ContractDocumentPrepareRequest intent);
Mono<ContractDocumentOperation> get(UUID tenant, UUID contract, UUID action);
Mono<ContractDocumentFactPreparation> facts(UUID tenant, UUID contract, UUID action);
Mono<ContractDocumentOperation> seal( UUID tenant, UUID contract, UUID action, ContractDocumentEnrichment enrichment);
Mono<DocumentIssuanceReceipt> issue( UUID tenant, UUID contract, UUID action, DocumentIssueRequest retainedRequest);
Mono<ContractDocumentOperation> record(UUID tenant, UUID contract, UUID action, UUID issuance);
Mono<ContractDocumentOperation> attach(UUID tenant, UUID contract, UUID action);
Mono<DocumentIssuedContent> content(UUID tenant, UUID contract, UUID action, UUID issuance);
```

### ESignaturePort

[ESignaturePort](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/ports/ESignaturePort.java)

```java
Mono<SignatureRequestResult> requestSignature(SignatureRequest request);
Mono<SignatureVerificationResult> verifySignature(String signatureRequestId);
```

### SignatureRequestContextPort

[SignatureRequestContextPort](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/ports/SignatureRequestContextPort.java)

```java
Mono<Context> current();
record Context(String tenantId, UUID signatureProviderId, boolean syntheticAllowed);
public Context(String tenantId, UUID signatureProviderId);
```

## Configuration

Values below come from source placeholders. Plain YAML values and configuration-property classes must also be reviewed at the linked configuration sources. Secret fallback values are deliberately redacted.

| Key | Fallback | Source |
| --- | --- | --- |
| `firefly.security.resource-server.strict-policy` | `false` | [GovernedDocumentAccessGuard](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedDocumentAccessGuard.java) |
| `firefly.contracts.documents.peers.customers.base-url` | Empty | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.products.base-url` | Empty | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.masters.base-url` | Empty | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.customers.allow-loopback-http` | `false` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.products.allow-loopback-http` | `false` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.masters.allow-loopback-http` | `false` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `firefly.contracts.documents.peers.contracts.base-url` | Empty | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `firefly.contracts.documents.peers.documents.base-url` | Empty | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `firefly.contracts.documents.peers.contracts.allow-loopback-http` | `false` | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `firefly.contracts.documents.peers.documents.allow-loopback-http` | `false` | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `spring.application.name` | No fallback | [DomainCommonContractsApplication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/DomainCommonContractsApplication.java) |
| `spring.application.version` | No fallback | [DomainCommonContractsApplication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/DomainCommonContractsApplication.java) |
| `spring.application.description` | No fallback | [DomainCommonContractsApplication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/DomainCommonContractsApplication.java) |
| `spring.application.team.name` | No fallback | [DomainCommonContractsApplication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/DomainCommonContractsApplication.java) |
| `spring.application.team.email` | No fallback | [DomainCommonContractsApplication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/DomainCommonContractsApplication.java) |
| `firefly.contracts.documents.security.issuer` | Empty | [GovernedDocumentSecurityConfiguration](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityConfiguration.java) |
| `firefly.contracts.documents.security.audience` | Empty | [GovernedDocumentSecurityConfiguration](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityConfiguration.java) |
| `firefly.contracts.documents.security.jwk-set-uri` | Empty | [GovernedDocumentSecurityConfiguration](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityConfiguration.java) |
| `ENDPOINT_CORE_CONTRACT_MGMT` | `http://localhost:8082` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `ENDPOINT_CORE_DOCUMENT_MGMT` | `http://localhost:8084` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `ENDPOINT_CORE_PRODUCT_MGMT` | `http://localhost:8086` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `FIREFLY_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `SERVER_ADDRESS` | `localhost` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `SERVER_PORT` | `8080` | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `endpoints.core.common-platform-contract-mgmt` | No fallback | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `endpoints.core.common-platform-document-mgmt` | No fallback | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |
| `endpoints.core.common-platform-product-mgmt` | No fallback | [application](../../domain-common-contracts-web/src/main/resources/application.yaml) |

## Security and failure evidence

| Declaration or failure site | Source |
| --- | --- |
| `new ResponseStatusException(HttpStatus.CONFLICT, "GOVERNED_DOCUMENT_INTENT_REQUIRED");` | [LegacyDocumentMigration](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/LegacyDocumentMigration.java) |
| `new ResponseStatusException(` | [CapturedContractEnrichmentCollector](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/CapturedContractEnrichmentCollector.java) |
| `HttpStatus.UNPROCESSABLE_ENTITY` | [CapturedContractEnrichmentCollector](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/CapturedContractEnrichmentCollector.java) |
| `new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_PEER_INVALID");` | [CapturedContractEnrichmentCollector](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/CapturedContractEnrichmentCollector.java) |
| `new ResponseStatusException(HttpStatusCode.valueOf(code), reason);` | [GovernedContractDocumentCoordinator](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedContractDocumentCoordinator.java) |
| `new ResponseStatusException(HttpStatus.FORBIDDEN, "GOVERNED_DOCUMENT_ACCESS_DENIED");` | [GovernedDocumentAccessGuard](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedDocumentAccessGuard.java) |
| `new ResponseStatusException(HttpStatus.BAD_REQUEST, "GOVERNED_DOCUMENT_INTENT_INVALID");` | [SealedContractDocumentRequest](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/SealedContractDocumentRequest.java) |
| `new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GOVERNED_DOCUMENT_PEER_INVALID");` | [SealedContractDocumentRequest](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/SealedContractDocumentRequest.java) |
| `new RuntimeException(` | [ConfirmContractSignatureHandler](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/handlers/ConfirmContractSignatureHandler.java) |
| `new IllegalStateException(` | [RemoveContractHandler](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/handlers/RemoveContractHandler.java) |
| `new RuntimeException(` | [RequestContractSignatureHandler](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/handlers/RequestContractSignatureHandler.java) |
| `new IllegalStateException(` | [ContractServiceImpl](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/services/impl/ContractServiceImpl.java) |
| `new IllegalStateException("Saga did not return a contract id")));` | [ContractServiceImpl](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/services/impl/ContractServiceImpl.java) |
| `new IllegalStateException("Contract was not found"));` | [ChangeAgreementStatusSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/ChangeAgreementStatusSaga.java) |
| `new IllegalStateException(` | [ChangeAgreementStatusSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/ChangeAgreementStatusSaga.java) |
| `new IllegalArgumentException("A signature request ID is required"));` | [ConfirmSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/ConfirmSignatureSaga.java) |
| `new IllegalStateException("The signature provider returned no verification")))` | [ConfirmSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/ConfirmSignatureSaga.java) |
| `new IllegalStateException("The requested signature has not been verified"));` | [ConfirmSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/ConfirmSignatureSaga.java) |
| `new IllegalStateException("Core returned no signature submission")))` | [RequestSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSaga.java) |
| `new IllegalStateException("Signature submission is not accepted or has an unknown outcome"));` | [RequestSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSaga.java) |
| `new IllegalStateException("Durable signature request identity is missing")) : Mono.just(id);` | [RequestSignatureSaga](../../domain-common-contracts-core/src/main/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSaga.java) |
| `new IllegalStateException("Core returned no signature submission")))` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("Synthetic signatures are not permitted for this actor context"));` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("Core signature association does not match"));` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("Core returned no signature verification")))` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("Synthetic signatures are not permitted for this actor context"));` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("Core signature ownership does not match"));` | [DocumentManagementESignatureAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/DocumentManagementESignatureAdapter.java) |
| `new IllegalStateException("A validated signature actor context adapter is required"));` | [SignatureRequestContextConfiguration](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/SignatureRequestContextConfiguration.java) |
| `new IllegalStateException("Explicit synthetic tenant and signature provider are required"));` | [SignatureRequestContextConfiguration](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/SignatureRequestContextConfiguration.java) |
| `new ResponseStatusException(` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `new ResponseStatusException(` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `HttpStatus.BAD_GATEWAY` | [ContractDocumentSourceSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapter.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [GovernedDocumentRelay](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelay.java) |
| `new ResponseStatusException(` | [GovernedDocumentRelay](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelay.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [GovernedDocumentRelay](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelay.java) |
| `new ResponseStatusException(HttpStatus.FORBIDDEN, "GOVERNED_DOCUMENT_DELEGATION_DENIED");` | [GovernedDocumentRelay](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelay.java) |
| `new IllegalArgumentException();` | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `new ResponseStatusException(HttpStatusCode.valueOf(status), reason);` | [GovernedDocumentSdkAdapter](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapter.java) |
| `new ResponseStatusException(` | [GovernedPeerEndpoint](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedPeerEndpoint.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [GovernedPeerEndpoint](../../domain-common-contracts-infra/src/main/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedPeerEndpoint.java) |
| `HttpStatus.CREATED` | [ContractController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractController.java) |
| `HttpStatus.ACCEPTED` | [ContractDocumentController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentController.java) |
| `HttpStatus.CREATED` | [ContractPartyController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyController.java) |
| `HttpStatus.ACCEPTED` | [ContractSignatureController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractSignatureController.java) |
| `HttpStatus.CREATED` | [ContractTermsController](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractTermsController.java) |
| `new ResponseStatusException(HttpStatus.BAD_REQUEST, "GOVERNED_DOCUMENT_INTENT_INVALID");` | [GovernedContractDocumentInputs](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedContractDocumentInputs.java) |
| `new OAuth2AuthenticationException(` | [GovernedDocumentAuthentication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentAuthentication.java) |
| `new OAuth2Error("invalid_token"), "Invalid governed document authentication");` | [GovernedDocumentAuthentication](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentAuthentication.java) |
| `new JwtException("Governed document trust is not configured"));` | [GovernedDocumentSecurityConfiguration](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityConfiguration.java) |
| `HttpStatus.FORBIDDEN` | [GovernedDocumentSecurityConfiguration](../../domain-common-contracts-web/src/main/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityConfiguration.java) |

## Build and dependencies

### domain-common-contracts-core

[domain-common-contracts-core/pom.xml](../../domain-common-contracts-core/pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts-core:26.10.6`. Parent: `io.fireflysoftware:domain-common-contracts:26.10.6`.

- `io.fireflysoftware:core-common-contract-mgmt-sdk:`
- `io.fireflysoftware:core-common-document-mgmt-sdk:`
- `io.fireflysoftware:domain-common-contracts-infra:`
- `io.projectreactor:reactor-test:`
- `org.fireflyframework:fireflyframework-security-spi:`
- `org.fireflyframework:fireflyframework-starter-domain:`
- `org.fireflyframework:fireflyframework-utils:`
- `org.mapstruct:mapstruct:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### domain-common-contracts-infra

[domain-common-contracts-infra/pom.xml](../../domain-common-contracts-infra/pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts-infra:26.10.6`. Parent: `io.fireflysoftware:domain-common-contracts:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `io.fireflysoftware:core-common-contract-mgmt-sdk:`
- `io.fireflysoftware:core-common-customer-mgmt-sdk:`
- `io.fireflysoftware:core-common-document-mgmt-sdk:`
- `io.fireflysoftware:core-common-product-mgmt-sdk:`
- `io.fireflysoftware:core-common-reference-master-data-sdk:`
- `io.projectreactor:reactor-test:`
- `io.swagger.core.v3:swagger-annotations:`
- `org.fireflyframework:fireflyframework-security-spi:`
- `org.fireflyframework:fireflyframework-utils:`
- `org.fireflyframework:fireflyframework-validators:`
- `org.postgresql:postgresql:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-validation:`
- `org.springframework.boot:spring-boot-starter-webflux:`
- `org.springframework.boot:spring-boot-starter:`
- `org.springframework.security:spring-security-oauth2-jose:`
- `org.springframework.security:spring-security-oauth2-resource-server:`
- `org.testcontainers:testcontainers-junit-jupiter:`
- `org.testcontainers:testcontainers-postgresql:`

### domain-common-contracts-interfaces

[domain-common-contracts-interfaces/pom.xml](../../domain-common-contracts-interfaces/pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts-interfaces:26.10.6`. Parent: `io.fireflysoftware:domain-common-contracts:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `io.fireflysoftware:domain-common-contracts-core:`
- `io.swagger.core.v3:swagger-annotations:`
- `org.fireflyframework:fireflyframework-utils:`
- `org.fireflyframework:fireflyframework-validators:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-validation:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### domain-common-contracts-sdk

[domain-common-contracts-sdk/pom.xml](../../domain-common-contracts-sdk/pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts-sdk:26.10.6`. Parent: `io.fireflysoftware:domain-common-contracts:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `com.squareup.okhttp3:logging-interceptor:`
- `com.squareup.okhttp3:okhttp:`
- `io.fireflysoftware:core-common-config-mgmt-sdk:`
- `io.fireflysoftware:domain-common-contracts-web:`
- `io.swagger.core.v3:swagger-annotations:`
- `jakarta.annotation:jakarta.annotation-api:`
- `javax.annotation:javax.annotation-api:1.3.2`
- `org.flywaydb:flyway-database-postgresql:`
- `org.openapitools:jackson-databind-nullable:0.2.8`
- `org.postgresql:postgresql:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework:spring-webflux:`
- `org.testcontainers:testcontainers-junit-jupiter:`
- `org.testcontainers:testcontainers-postgresql:`

### domain-common-contracts-web

[domain-common-contracts-web/pom.xml](../../domain-common-contracts-web/pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts-web:26.10.6`. Parent: `io.fireflysoftware:domain-common-contracts:26.10.6`.

- `io.fireflysoftware:domain-common-contracts-core:`
- `io.fireflysoftware:domain-common-contracts-interfaces:`
- `io.micrometer:micrometer-registry-prometheus:`
- `io.projectreactor:reactor-test:`
- `org.fireflyframework:fireflyframework-security-resource-server:`
- `org.fireflyframework:fireflyframework-web:`
- `org.projectlombok:lombok:`
- `org.springdoc:springdoc-openapi-starter-webflux-ui:`
- `org.springframework.boot:spring-boot-starter-actuator:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-validation:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### domain-common-contracts

[pom.xml](../../pom.xml)

Coordinates: `io.fireflysoftware:domain-common-contracts:26.10.6`. Parent: `io.fireflysoftware:firefly-parent:26.10.6`.

- `io.fireflysoftware:domain-common-contracts-core:${project.version}`
- `io.fireflysoftware:domain-common-contracts-infra:${project.version}`
- `io.fireflysoftware:domain-common-contracts-interfaces:${project.version}`
- `io.fireflysoftware:domain-common-contracts-sdk:${project.version}`
- `io.fireflysoftware:domain-common-contracts-web:${project.version}`

## Test sources

- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/TestContractsCoreApplication.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/TestContractsCoreApplication.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/KitDocumentServiceTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/KitDocumentServiceTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/LegacyDocumentMigrationTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/LegacyDocumentMigrationTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/CapturedContractEnrichmentCollectorTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/CapturedContractEnrichmentCollectorTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedContractDocumentCoordinatorTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedContractDocumentCoordinatorTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedDocumentAccessGuardTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/GovernedDocumentAccessGuardTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/SealedContractDocumentRequestTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/documents/v1/SealedContractDocumentRequestTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/AddContractPartyHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/AddContractPartyHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/AddContractTermsHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/AddContractTermsHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/ConfirmContractSignatureHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/ConfirmContractSignatureHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/CreateContractHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/CreateContractHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GenerateContractDocumentHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GenerateContractDocumentHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetActiveContractsByPartyHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetActiveContractsByPartyHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDetailHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDetailHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDocumentHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDocumentHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDocumentsHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractDocumentsHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractPartiesHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractPartiesHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractStatusHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractStatusHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractStatusHistoryHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractStatusHistoryHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractTermsHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractTermsHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractsByPartyHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/GetContractsByPartyHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/RemoveContractPartyHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/RemoveContractPartyHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/RequestContractSignatureHandlerTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/handlers/RequestContractSignatureHandlerTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/ConfirmSignatureSagaTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/ConfirmSignatureSagaTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/GenerateDocumentSagaTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/GenerateDocumentSagaTest.java)
- [domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSagaTest.java](../../domain-common-contracts-core/src/test/java/io/fireflysoftware/domain/common/contracts/core/workflows/RequestSignatureSagaTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SandboxESignatureAdapterTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SandboxESignatureAdapterTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SignatureCoreSdkDatabaseTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SignatureCoreSdkDatabaseTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SignatureRequestContextConfigurationTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/SignatureRequestContextConfigurationTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapterTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/ContractDocumentSourceSdkAdapterTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentDelegationConfigurationTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentDelegationConfigurationTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelayTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentRelayTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapterTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedDocumentSdkAdapterTest.java)
- [domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedPeerEndpointTest.java](../../domain-common-contracts-infra/src/test/java/io/fireflysoftware/domain/common/contracts/infra/documents/v1/GovernedPeerEndpointTest.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/ContractDocumentRuntimeBootstrap.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/ContractDocumentRuntimeBootstrap.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/ContractRuntimeSupport.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/ContractRuntimeSupport.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedContractDocumentGeneratedContractTest.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedContractDocumentGeneratedContractTest.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedContractDocumentRuntimeIT.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedContractDocumentRuntimeIT.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedDocumentRuntimeBootstrap.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/GovernedDocumentRuntimeBootstrap.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/OwnedPeerProxy.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/OwnedPeerProxy.java)
- [domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/PrivateIssuer.java](../../domain-common-contracts-sdk/src/test/java/io/fireflysoftware/qualification/contracts/PrivateIssuer.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractDocumentControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractPartyControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractSignatureControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractSignatureControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractTermsControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/controllers/ContractTermsControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedContractDocumentControllerTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedContractDocumentControllerTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedContractDocumentInputsTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedContractDocumentInputsTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentAuthenticationTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentAuthenticationTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentDefaultPolicyHttpTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentDefaultPolicyHttpTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentHttpTestSupport.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentHttpTestSupport.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentMissingTrustHttpTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentMissingTrustHttpTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityHttpTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentSecurityHttpTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentStrictPolicyDisabledHttpTest.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/documents/v1/GovernedDocumentStrictPolicyDisabledHttpTest.java)
- [domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/openapi/OpenApiGenApplication.java](../../domain-common-contracts-web/src/test/java/io/fireflysoftware/domain/common/contracts/web/openapi/OpenApiGenApplication.java)

## CI definitions

- [.github/workflows/maven-central.yml](../../.github/workflows/maven-central.yml)
- [.github/workflows/release.yml](../../.github/workflows/release.yml)
