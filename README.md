# domain-common-contracts

Domain layer microservice responsible for orchestrating contract management operations. This service acts as the domain orchestration layer between API consumers and the `core-common-contract-mgmt` and `core-common-document-mgmt` management services, coordinating contract lifecycle workflows including party management, terms configuration, document generation, and e-signature processing through a CQRS-based architecture.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [Module Structure](#module-structure)
  - [Tech Stack](#tech-stack)
  - [CQRS Command and Query Handlers](#cqrs-command-and-query-handlers)
- [Setup](#setup)
  - [Prerequisites](#prerequisites)
  - [Environment Variables](#environment-variables)
  - [Application Configuration](#application-configuration)
  - [Build](#build)
  - [Run](#run)
- [API Endpoints](#api-endpoints)
  - [Contracts](#contracts)
  - [Contract Parties](#contract-parties)
  - [Contract Terms](#contract-terms)
  - [Contract Documents](#contract-documents)
  - [Contract Signatures](#contract-signatures)
  - [Common Headers](#common-headers)
- [SDK](#sdk)
- [Testing](#testing)

## Overview

The Domain Common Contracts service manages the full lifecycle of contracts, their associated parties, terms, documents, and signatures:

- **Contract Management** -- Supports creating contracts, retrieving contract details, listing contracts by party, and querying active contracts for a given party.
- **Party Management** -- Enables adding and removing parties to and from contracts, with role-based association, and retrieving all parties for a contract.
- **Terms Management** -- Allows attaching terms to contracts based on term templates, and listing all terms associated with a contract.
- **Document Generation** -- Orchestrates document creation through the `core-common-document-mgmt` service and links generated documents to contracts via the `core-common-contract-mgmt` service.
- **E-Signature Workflow** -- Supports requesting e-signatures on contract documents and confirming signatures, with contract status transitions recorded in the status history. An `ESignaturePort` abstraction allows pluggable signature providers (a stub adapter is included for development).
- **Status Tracking** -- Provides current contract status retrieval and full status history auditing.
- **Event-Driven Architecture** -- Publishes domain events to Kafka via the FireflyFramework EDA module for downstream consumers.

## Architecture

### Module Structure

| Module | Description |
|--------|-------------|
| `domain-common-contracts-core` | Business logic: commands, queries, command/query handlers, service interfaces and implementations, MapStruct mappers |
| `domain-common-contracts-interfaces` | Interface adapters connecting core to infrastructure and external boundaries |
| `domain-common-contracts-infra` | Infrastructure layer: API client factories, configuration properties, e-signature port and stub adapter |
| `domain-common-contracts-web` | Spring Boot WebFlux application: REST controllers, application entry point, configuration |
| `domain-common-contracts-sdk` | Auto-generated client SDK from OpenAPI spec for downstream consumers |

### Tech Stack

- **Java 25**
- **Spring Boot** with **WebFlux** (reactive, non-blocking)
- **[FireflyFramework](https://github.com/fireflyframework/)** -- Parent POM (`firefly-parent`) and libraries:
  - `fireflyframework-web` -- Common web configurations
  - `fireflyframework-starter-domain` -- Domain layer CQRS support
  - `fireflyframework-utils` -- Shared utilities
  - `fireflyframework-validators` -- Validation framework
- **FireflyFramework CQRS** -- `CommandBus` and `QueryBus` for command and query dispatch
- **FireflyFramework EDA** -- Event-driven architecture with Kafka publisher for domain events
- **FireflyFramework Step Events** -- `stepevents` support for publishing domain lifecycle events
- **Project Reactor** (`Mono`/`Flux`) -- Reactive streams throughout
- **MapStruct** -- Object mapping between commands and SDK DTOs
- **Lombok** -- Boilerplate reduction
- **SpringDoc OpenAPI** -- API documentation and Swagger UI
- **Micrometer + Prometheus** -- Metrics export
- **Spring Boot Actuator** -- Health checks and operational endpoints
- **OpenAPI Generator** -- SDK generation from the OpenAPI spec (WebClient-based reactive client)
- **Virtual Threads** -- Java virtual threads enabled for improved concurrency

### CQRS Command and Query Handlers

The service dispatches all operations through `CommandBus` and `QueryBus`. The following handlers are implemented:

| Handler | Type | Description |
|---------|------|-------------|
| `CreateContractHandler` | Command | Creates a new contract via the `ContractsApi` SDK |
| `AddContractPartyHandler` | Command | Adds a party to a contract via the `ContractPartiesApi` SDK |
| `RemoveContractPartyHandler` | Command | Removes a party from a contract via the `ContractPartiesApi` SDK |
| `AddContractTermsHandler` | Command | Adds terms to a contract via the `ContractTermsApi` SDK |
| `GenerateContractDocumentHandler` | Command | Creates a document in the document management system and links it to the contract |
| `RequestContractSignatureHandler` | Command | Requests an e-signature via the `ESignaturePort` and records a `SUBMITTED_FOR_APPROVAL` status transition |
| `ConfirmContractSignatureHandler` | Command | Verifies a signature via the `ESignaturePort` and records an `APPROVED` status transition |
| `GetContractDetailHandler` | Query | Retrieves the details of a specific contract |
| `GetContractsByPartyHandler` | Query | Retrieves all contracts for a party |
| `GetActiveContractsByPartyHandler` | Query | Retrieves all active contracts for a party |
| `GetContractStatusHandler` | Query | Retrieves the current status of a contract |
| `GetContractStatusHistoryHandler` | Query | Retrieves the full status history of a contract |
| `GetContractPartiesHandler` | Query | Retrieves all parties for a contract |
| `GetContractTermsHandler` | Query | Retrieves all terms for a contract |
| `GetContractDocumentsHandler` | Query | Retrieves all documents for a contract |
| `GetContractDocumentHandler` | Query | Retrieves a specific document for a contract |

## Setup

### Prerequisites

- **Java 25**
- **Maven 3.9+**
- Access to the FireflyFramework Maven repository for parent POM and BOM dependencies
- Running instance of `core-common-contract-mgmt` service (or its API accessible at the configured base path)
- Running instance of `core-common-document-mgmt` service (or its API accessible at the configured base path)
- Kafka broker accessible at the configured bootstrap servers (for event publishing)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ENDPOINT_CORE_CONTRACT_MGMT` | `http://localhost:8082` | Base URL for the downstream contract management service |
| `ENDPOINT_CORE_DOCUMENT_MGMT` | `http://localhost:8084` | Base URL for the downstream document management service |
| `FIREFLY_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers for event publishing |
| `SERVER_ADDRESS` | `localhost` | Server bind address |
| `SERVER_PORT` | `8080` | Server port |

### Application Configuration

The service defines its configuration in `application.yaml`:

```yaml
spring:
  application:
    name: domain-common-contracts
    version: 1.0.0
    description: Domain Contract Management Layer Application
    team:
      name: Firefly Software Solutions Inc
      email: dev@getfirefly.io
  threads:
    virtual:
      enabled: true

endpoints:
  core:
    common-platform-contract-mgmt: ${ENDPOINT_CORE_CONTRACT_MGMT:http://localhost:8082}
    common-platform-document-mgmt: ${ENDPOINT_CORE_DOCUMENT_MGMT:http://localhost:8084}

firefly:
  cqrs:
    enabled: true
    command:
      timeout: 30s
      metrics-enabled: true
      tracing-enabled: true
    query:
      timeout: 15s
      caching-enabled: true
      cache-ttl: 15m
  saga.performance.enabled: true

  eda:
    enabled: true
    default-publisher-type: KAFKA
    default-connection-id: default
    publishers:
      kafka:
        default:
          enabled: true
          default-topic: domain-layer
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

  stepevents:
    enabled: true
```

Additional configuration provided externally or by a config server typically includes:

- `api-configuration.common-platform.contract-mgmt.base-path` -- Base URL for the downstream contract management service
- `api-configuration.common-platform.document-mgmt.base-path` -- Base URL for the downstream document management service
- Server port, logging levels, and profile-specific settings

### Build

```bash
mvn clean install
```

### Run

```bash
mvn -pl domain-common-contracts-web spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar domain-common-contracts-web/target/domain-common-contracts.jar
```

## API Endpoints

### Contracts

Base path: `/api/v1/contracts`

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/contracts` | Create Contract | Create a new contract with a party, contract type, and description |
| `GET` | `/api/v1/contracts/{contractId}` | Get Contract Detail | Retrieve the details of a specific contract |
| `GET` | `/api/v1/contracts/by-party/{partyId}` | List Contracts by Party | Retrieve all contracts associated with a party |
| `GET` | `/api/v1/contracts/by-party/{partyId}/active` | List Active Contracts by Party | Retrieve all active contracts associated with a party |
| `GET` | `/api/v1/contracts/{contractId}/status` | Get Contract Status | Retrieve the current status of a contract |
| `GET` | `/api/v1/contracts/{contractId}/status-history` | Get Contract Status History | Retrieve the full status history of a contract |

### Contract Parties

Base path: `/api/v1/contracts/{contractId}/parties`

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/parties` | Add Party | Add a party to a contract with a specified role |
| `GET` | `/api/v1/contracts/{contractId}/parties` | List Parties | Retrieve all parties associated with a contract |
| `DELETE` | `/api/v1/contracts/{contractId}/parties/{partyId}` | Remove Party | Remove a party from a contract |

### Contract Terms

Base path: `/api/v1/contracts/{contractId}/terms`

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/terms` | Add Terms | Add terms to a contract using a term template and value |
| `GET` | `/api/v1/contracts/{contractId}/terms` | List Terms | Retrieve all terms associated with a contract |

### Contract Documents

Base path: `/api/v1/contracts/{contractId}/documents`

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/documents/generate` | Generate Document | Generate a document for a contract using a template |
| `GET` | `/api/v1/contracts/{contractId}/documents` | List Documents | Retrieve all documents associated with a contract |

### Contract Signatures

Base path: `/api/v1/contracts/{contractId}/signature`

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/contracts/{contractId}/signature/request` | Request Signature | Request an e-signature on a contract document from a specified signer party |
| `POST` | `/api/v1/contracts/{contractId}/signature/confirm` | Confirm Signature | Confirm a previously requested contract signature |

### Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Idempotency-Key` | No | Ensures identical requests are processed only once |
| `X-Party-ID` | Conditional | Client identifier (at least one identity header required) |
| `X-Employee-ID` | Conditional | Employee identifier |
| `X-Service-Account-ID` | Conditional | Service account identifier |
| `X-Auth-Roles` | No | Comma-separated roles (CUSTOMER, ADMIN, CUSTOMER_SUPPORT, SUPERVISOR, MANAGER, BRANCH_STAFF, SERVICE_ACCOUNT) |
| `X-Auth-Scopes` | No | Comma-separated OAuth2 scopes |
| `X-Request-ID` | No | Request traceability identifier |

## SDK

The `domain-common-contracts-sdk` module auto-generates a Java client SDK from the OpenAPI specification produced by the web module. The SDK is generated using the OpenAPI Generator Maven Plugin with the `webclient` library, producing a reactive WebClient-based client.

**Generated packages:**

- `com.firefly.domain.common.contracts.sdk.api` -- API client classes
- `com.firefly.domain.common.contracts.sdk.model` -- Request and response model classes
- `com.firefly.domain.common.contracts.sdk.invoker` -- HTTP client infrastructure

Downstream services can depend on this SDK to consume the domain-common-contracts API without writing manual HTTP client code. Do not modify generated code directly; instead, update the controllers and rebuild to regenerate the SDK.

## Testing

Run all tests:

```bash
mvn clean test
```

Run tests for a specific module:

```bash
mvn -pl domain-common-contracts-core test
mvn -pl domain-common-contracts-infra test
mvn -pl domain-common-contracts-web test
```

## Monitoring

The service exposes the following operational endpoints via Spring Boot Actuator:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health status (with liveness and readiness probes) |
| `/actuator/info` | Application information |
| `/actuator/prometheus` | Prometheus metrics endpoint |

OpenAPI documentation is available at:

- **Swagger UI**: [http://localhost:{port}/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Docs (JSON)**: [http://localhost:{port}/v3/api-docs](http://localhost:8080/v3/api-docs)

## Repository

[https://github.com/firefly-oss/domain-common-contracts](https://github.com/firefly-oss/domain-common-contracts)
