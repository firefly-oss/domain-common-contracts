# domain-common-contracts

> Domain-layer microservice that orchestrates the full contract lifecycle by composing the `core-common-contract-mgmt` and `core-common-document-mgmt` platform services.

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Module Structure](#module-structure)
- [API Endpoints](#api-endpoints)
- [Domain Logic](#domain-logic)
- [Dependencies](#dependencies)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Testing](#testing)

## Overview

`domain-common-contracts` is a reactive Spring WebFlux microservice that sits in the domain layer of the Firefly platform. It exposes a REST API covering the entire contract lifecycle: creation, party and terms management, document generation, and e-signature request and confirmation. The service does not own its own persistence; instead it orchestrates two downstream platform services — `core-common-contract-mgmt` and `core-common-document-mgmt` — through their respective generated SDK clients.

Business logic is modelled using the Firefly Framework's CQRS and Saga patterns. Write operations are expressed as commands dispatched through a `CommandBus`; read operations are expressed as queries dispatched through a `QueryBus`. Multi-step workflows that require atomicity and compensation — document generation and the e-signature flow — are implemented as sagas executed by the `SagaEngine`. Each saga declares a directed acyclic graph of steps with explicit compensation actions, allowing the engine to roll back partial work on failure.

The service publishes domain events to a Kafka topic (`domain-layer`) via the Firefly EDA module and emits step lifecycle events through the `stepevents` module. It also ships an auto-generated SDK (`domain-common-contracts-sdk`) built from its own OpenAPI specification so that other services can call it without writing manual HTTP client code.

## Architecture

The service follows a CQRS + Saga pattern layered over a hexagonal (ports-and-adapters) structure:

```
HTTP Request
     │
     ▼
[domain-common-contracts-web]       ← REST controllers (Spring WebFlux)
     │
     ▼
[domain-common-contracts-core]      ← CommandBus / QueryBus / SagaEngine
     │
     │  Commands → CommandHandlers ──► direct SDK call
     │                             └─► SagaEngine (generate-document-saga,
     │                                             request-signature-saga,
     │                                             confirm-signature-saga)
     │  Queries  → QueryHandlers   ──► direct SDK call
     │
     ├──► ContractMgmtClientFactory  ──►  core-common-contract-mgmt  (:8082)
     │         com.firefly.core.contract.sdk
     │
     └──► DocumentMgmtClientFactory  ──►  core-common-document-mgmt  (:8084)
               com.firefly.commons.ecm.sdk

ESignaturePort (port interface, domain-common-contracts-infra)
     └── StubESignatureAdapter  (active when integration.esignature.provider=stub)
```

Saga compensation runs in reverse step order. The `generate-document-saga` compensates by detaching the contract-document link then deleting the ECM document. The `request-signature-saga` compensates by cancelling the signature record on a best-effort basis; provider state is not reversed. The `confirm-signature-saga` logs a warning on status reversion because rolling back an APPROVED status requires an explicit business decision.

## Module Structure

| Module | Purpose |
|--------|---------|
| `domain-common-contracts-interfaces` | Reserved for shared interface definitions consumed across modules; currently a placeholder with no Java sources |
| `domain-common-contracts-core` | CQRS commands, queries, command and query handlers, saga workflows, domain service interface (`ContractService`) and its `CommandBus`/`QueryBus`-based implementation, and MapStruct mappers |
| `domain-common-contracts-infra` | SDK client factories (`ContractMgmtClientFactory`, `DocumentMgmtClientFactory`), configuration properties beans (`ContractMgmtProperties`, `DocumentMgmtProperties`), `ESignaturePort` interface, and `StubESignatureAdapter` |
| `domain-common-contracts-web` | Spring Boot application entry point (`DomainCommonContractsApplication`), all five REST controllers, and `application.yaml`; the runnable artifact |
| `domain-common-contracts-sdk` | Reactive WebClient-based Java client SDK auto-generated from the service's own OpenAPI specification at build time via the OpenAPI Generator Maven Plugin |

## API Endpoints

### Contracts

| Method | Path | Description | Status Code |
|--------|------|-------------|-------------|
| `POST` | `/api/v1/contracts` | Create a new contract | `201 Created` |
| `GET` | `/api/v1/contracts/{contractId}` | Retrieve full contract details | `200 OK` |
| `GET` | `/api/v1/contracts/by-party/{partyId}` | List all contracts for a party | `200 OK` |
| `GET` | `/api/v1/contracts/by-party/{partyId}/active` | List active contracts for a party | `200 OK` |
| `GET` | `/api/v1/contracts/{contractId}/status` | Retrieve current contract status | `200 OK` |
| `GET` | `/api/v1/contracts/{contractId}/status-history` | Retrieve full status history | `200 OK` |

### Contract Parties

| Method | Path | Description | Status Code |
|--------|------|-------------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/parties` | Add a party to a contract | `201 Created` |
| `GET` | `/api/v1/contracts/{contractId}/parties` | List all parties on a contract | `200 OK` |
| `DELETE` | `/api/v1/contracts/{contractId}/parties/{partyId}` | Remove a party from a contract | `204 No Content` |

### Contract Terms

| Method | Path | Description | Status Code |
|--------|------|-------------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/terms` | Add a term to a contract | `201 Created` |
| `GET` | `/api/v1/contracts/{contractId}/terms` | List all terms on a contract | `200 OK` |

### Contract Documents

| Method | Path | Description | Status Code |
|--------|------|-------------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/documents/generate` | Generate and attach a document to a contract | `202 Accepted` |
| `GET` | `/api/v1/contracts/{contractId}/documents` | List all documents on a contract | `200 OK` |
| `GET` | `/api/v1/contracts/{contractId}/documents/{docId}` | Retrieve a specific contract document | `200 OK` |

### Contract Signatures

| Method | Path | Description | Status Code |
|--------|------|-------------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/signature/request` | Request an e-signature on a contract document | `202 Accepted` |
| `POST` | `/api/v1/contracts/{contractId}/signature/confirm` | Confirm a previously requested signature | `200 OK` |

OpenAPI documentation is available at `/swagger-ui.html` and the machine-readable spec at `/v3/api-docs` when the application is running.

## Domain Logic

### Command Handlers

All handlers extend `CommandHandler<C, R>` and are registered with the `CommandBus` via `@CommandHandlerComponent`.

| Handler | Command | Behaviour |
|---------|---------|-----------|
| `CreateContractHandler` | `CreateContractCommand` | Maps command to `ContractDTO` via `ContractCommandMapper` and calls `ContractsApi.createContract`; returns the new `contractId` |
| `AddContractPartyHandler` | `AddContractPartyCommand` | Maps to `ContractPartyDTO` and calls `ContractPartiesApi.createContractParty`; returns `contractPartyId` |
| `RemoveContractPartyHandler` | `RemoveContractPartyCommand` | Calls `ContractPartiesApi.deleteContractParty` |
| `AddContractTermsHandler` | `AddContractTermsCommand` | Maps to `ContractTermDynamicDTO` and calls `ContractTermsApi.createContractTerm`; returns `termId` |
| `GenerateContractDocumentHandler` | `GenerateContractDocumentCommand` | Executes `generate-document-saga`; returns `contractDocumentId` from the final step |
| `RequestContractSignatureHandler` | `RequestContractSignatureCommand` | Executes `request-signature-saga`; returns the `contractStatusHistoryId` created in the final step |
| `ConfirmContractSignatureHandler` | `ConfirmContractSignatureCommand` | Executes `confirm-signature-saga`; returns `Void` on success |

### Query Handlers

All handlers extend `QueryHandler<Q, R>` and are registered with the `QueryBus` via `@QueryHandlerComponent`.

| Handler | Query | Downstream API |
|---------|-------|----------------|
| `GetContractDetailHandler` | `GetContractDetailQuery` | `ContractsApi.getContractById` |
| `GetContractsByPartyHandler` | `GetContractsByPartyQuery` | `GlobalContractPartiesApi.getContractPartiesByPartyId` |
| `GetActiveContractsByPartyHandler` | `GetActiveContractsByPartyQuery` | `GlobalContractPartiesApi` (active filter) |
| `GetContractPartiesHandler` | `GetContractPartiesQuery` | `ContractPartiesApi` |
| `GetContractTermsHandler` | `GetContractTermsQuery` | `ContractTermsApi` |
| `GetContractStatusHandler` | `GetContractStatusQuery` | `ContractStatusHistoryApi` |
| `GetContractStatusHistoryHandler` | `GetContractStatusHistoryQuery` | `ContractStatusHistoryApi` |
| `GetContractDocumentsHandler` | `GetContractDocumentsQuery` | `ContractDocumentsApi` |
| `GetContractDocumentHandler` | `GetContractDocumentQuery` | `ContractDocumentsApi` |

### Sagas

#### `generate-document-saga`

Atomically creates a document in ECM and links it to the contract record.

```
Step 1 — generate-document   (Layer 0)
  Action:      DocumentControllerApi.createDocument  (type: CONTRACT)
  Stores:      ctx["documentId"], ctx["contractId"]
  Compensate:  DocumentControllerApi.deleteDocument

Step 2 — attach-to-contract  (Layer 1, dependsOn: generate-document)
  Action:      ContractDocumentsApi.createContractDocument
  Reads:       ctx["documentId"]
  Stores:      ctx["contractDocumentId"]
  Compensate:  ContractDocumentsApi.deleteContractDocument
```

The handler returns the `contractDocumentId` produced by step 2.

#### `request-signature-saga`

Creates a signature record in the document management system, submits it to the e-signature provider, and records the `SUBMITTED_FOR_APPROVAL` status transition on the contract.

```
Step 1 — create-signature-request  (Layer 0)
  Action:      DocumentSignatureControllerApi.addDocumentSignature
  Stores:      ctx["signatureRecordId"], ctx["contractId"]
  Compensate:  DocumentSignatureControllerApi.deleteDocumentSignature  (best-effort)

Step 2 — send-to-provider          (Layer 1, dependsOn: create-signature-request)
  Action:      ESignaturePort.requestSignature
  Stores:      ctx["providerRequestId"]

Step 3 — update-contract-status    (Layer 2, dependsOn: send-to-provider)
  Action:      ContractStatusHistoryApi.createContractStatusHistory
               (status: SUBMITTED_FOR_APPROVAL)
```

The handler returns the `contractStatusHistoryId` produced by step 3.

#### `confirm-signature-saga`

Verifies the signature with the provider, records the `APPROVED` status on the contract, and sends a best-effort notification.

```
Step 1 — verify-signature          (Layer 0)
  Action:      ESignaturePort.verifySignature
  Stores:      ctx["verificationStatus"], ctx["contractId"]

Step 2 — update-contract-status    (Layer 1, dependsOn: verify-signature)
  Action:      ContractStatusHistoryApi.createContractStatusHistory  (status: APPROVED)
  Stores:      ctx["statusHistoryId"]
  Compensate:  Logs a warning — automated status reversion deferred to business decision

Step 3 — send-notification         (Layer 2, best-effort, dependsOn: update-contract-status)
  Action:      Stub returning "skipped"
               (TODO: replace with domain-common-notifications SDK call)
```

### E-Signature Port

`ESignaturePort` is a hexagonal port interface with two methods: `requestSignature(SignatureRequest)` and `verifySignature(signatureRequestId)`. The active adapter is selected by the `integration.esignature.provider` property.

`StubESignatureAdapter` is the default implementation (`@ConditionalOnProperty(matchIfMissing = true)`). It simulates both operations with a 100 ms delay and returns a successful result — `REQUESTED` status with a randomly generated `signatureRequestId` for request, and `VERIFIED` status for verification.

## Dependencies

### Upstream (consumes)

| Service | SDK Package | Client Factory | APIs exposed |
|---------|-------------|----------------|--------------|
| `core-common-contract-mgmt` | `com.firefly.core.contract.sdk` | `ContractMgmtClientFactory` | `ContractsApi`, `ContractPartiesApi`, `ContractTermsApi`, `ContractDocumentsApi`, `ContractStatusHistoryApi`, `ContractEventsApi`, `ContractRiskAssessmentsApi`, `GlobalContractPartiesApi`, `ContractTermTemplatesApi`, `ContractTermValidationRulesApi` |
| `core-common-document-mgmt` | `com.firefly.commons.ecm.sdk` | `DocumentMgmtClientFactory` | `DocumentControllerApi`, `DocumentSignatureControllerApi`, `SignatureRequestControllerApi`, `SignatureVerificationControllerApi`, `SignatureProviderControllerApi`, `DocumentVersionControllerApi`, `DocumentMetadataControllerApi`, `DocumentTagControllerApi`, `DocumentPermissionControllerApi`, `DocumentSearchControllerApi`, `FolderControllerApi`, `TagControllerApi` |

The base URL for each service is resolved at startup from configuration properties injected into `ContractMgmtProperties` (`api-configuration.common-platform.contract-mgmt.base-path`) and `DocumentMgmtProperties` (`api-configuration.common-platform.document-mgmt.base-path`).

### Downstream (consumed by)

Other services within the Firefly platform can integrate with `domain-common-contracts` using the `domain-common-contracts-sdk` Maven artifact. The SDK is a reactive WebClient-based Java client generated from this service's OpenAPI specification. Generated packages:

- `com.firefly.domain.common.contracts.sdk.api` — API client classes
- `com.firefly.domain.common.contracts.sdk.model` — Request and response model classes
- `com.firefly.domain.common.contracts.sdk.invoker` — HTTP client infrastructure

## Configuration

All properties are defined in `domain-common-contracts-web/src/main/resources/application.yaml`.

| Property | Default / Environment Variable | Description |
|----------|-------------------------------|-------------|
| `server.port` | `${SERVER_PORT:8080}` | HTTP server port |
| `server.address` | `${SERVER_ADDRESS:localhost}` | Bind address |
| `server.shutdown` | `graceful` | Enables graceful shutdown |
| `spring.threads.virtual.enabled` | `true` | Enables Java virtual threads |
| `endpoints.core.common-platform-contract-mgmt` | `${ENDPOINT_CORE_CONTRACT_MGMT:http://localhost:8082}` | Base URL of `core-common-contract-mgmt` |
| `endpoints.core.common-platform-document-mgmt` | `${ENDPOINT_CORE_DOCUMENT_MGMT:http://localhost:8084}` | Base URL of `core-common-document-mgmt` |
| `api-configuration.common-platform.contract-mgmt.base-path` | Resolves to `endpoints.core.common-platform-contract-mgmt` | Injected into `ContractMgmtProperties`; used by `ContractMgmtClientFactory` |
| `api-configuration.common-platform.document-mgmt.base-path` | Resolves to `endpoints.core.common-platform-document-mgmt` | Injected into `DocumentMgmtProperties`; used by `DocumentMgmtClientFactory` |
| `firefly.cqrs.enabled` | `true` | Enables the Firefly CQRS bus |
| `firefly.cqrs.command.timeout` | `30s` | Command execution timeout |
| `firefly.cqrs.command.metrics-enabled` | `true` | Enables command bus metrics |
| `firefly.cqrs.command.tracing-enabled` | `true` | Enables command bus distributed tracing |
| `firefly.cqrs.query.timeout` | `15s` | Query execution timeout |
| `firefly.cqrs.query.caching-enabled` | `true` | Enables query result caching |
| `firefly.cqrs.query.cache-ttl` | `15m` | Query cache time-to-live |
| `firefly.saga.performance.enabled` | `true` | Enables saga performance metrics |
| `firefly.eda.enabled` | `true` | Enables the Firefly EDA module |
| `firefly.eda.default-publisher-type` | `KAFKA` | Event publisher backend |
| `firefly.eda.publishers.kafka.default.default-topic` | `domain-layer` | Kafka topic for domain events |
| `firefly.eda.publishers.kafka.default.bootstrap-servers` | `${FIREFLY_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` | Kafka bootstrap servers |
| `firefly.stepevents.enabled` | `true` | Enables saga step event emission |
| `integration.esignature.provider` | `stub` | Selects the active `ESignaturePort` adapter (`stub` activates `StubESignatureAdapter`) |
| `springdoc.api-docs.path` | `/v3/api-docs` | OpenAPI specification endpoint |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI path |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Exposed Actuator endpoints |
| `management.health.livenessState.enabled` | `true` | Kubernetes liveness probe |
| `management.health.readinessState.enabled` | `true` | Kubernetes readiness probe |
| `logging.level.com.firefly` | `DEBUG` | Log level for all Firefly classes |

## Running Locally

Ensure that `core-common-contract-mgmt` is reachable on port `8082`, `core-common-document-mgmt` on port `8084`, and a Kafka broker on port `9092` (or override any of these via the environment variables listed in the Configuration section above).

```bash
mvn clean install -DskipTests
cd /Users/casanchez/Desktop/firefly-oss/domain-common-contracts
mvn spring-boot:run -pl domain-common-contracts-web
```

Server port: `8080`

To override the downstream service URLs or the server port at startup:

```bash
SERVER_PORT=9090 \
ENDPOINT_CORE_CONTRACT_MGMT=http://contract-mgmt-host:8082 \
ENDPOINT_CORE_DOCUMENT_MGMT=http://document-mgmt-host:8084 \
FIREFLY_KAFKA_BOOTSTRAP_SERVERS=kafka-host:9092 \
mvn spring-boot:run -pl domain-common-contracts-web
```

To regenerate the SDK after modifying the API, build the web module first so that the OpenAPI specification is written to `domain-common-contracts-web/target/openapi/openapi.yml`, then trigger source generation for the SDK module:

```bash
mvn package -pl domain-common-contracts-web -DskipTests
mvn generate-sources -pl domain-common-contracts-sdk
```

## Testing

```bash
mvn clean verify
```

Unit tests live under each module's `src/test/java` tree. The core module contains dedicated test classes for every command handler, query handler, and saga (`CreateContractHandlerTest`, `GenerateDocumentSagaTest`, `RequestSignatureSagaTest`, `ConfirmSignatureSagaTest`, and others). The infra module tests the `StubESignatureAdapter`. The web module contains controller slice tests for all five controllers.

To run tests for a specific module:

```bash
mvn test -pl domain-common-contracts-core
mvn test -pl domain-common-contracts-infra
mvn test -pl domain-common-contracts-web
```
