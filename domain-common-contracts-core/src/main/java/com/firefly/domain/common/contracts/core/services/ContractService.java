package com.firefly.domain.common.contracts.core.services;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for contract management operations.
 * Provides methods for CRUD operations on contracts, parties, terms, documents, and signatures.
 */
public interface ContractService {

    /**
     * Creates a new contract.
     *
     * @param partyId      the initiating party identifier
     * @param contractType the type of contract
     * @param description  a description of the contract
     * @return a {@link Mono} emitting the identifier of the created contract
     */
    Mono<UUID> createContract(UUID partyId, String contractType, String description);

    /**
     * Retrieves the details of a specific contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the contract details
     */
    Mono<Object> getContractDetail(UUID contractId);

    /**
     * Adds a party to a contract.
     *
     * @param contractId the contract identifier
     * @param partyId    the party identifier
     * @param role       the role of the party
     * @return a {@link Mono} emitting the identifier of the created contract party
     */
    Mono<UUID> addContractParty(UUID contractId, UUID partyId, String role);

    /**
     * Removes a party from a contract.
     *
     * @param contractId the contract identifier
     * @param partyId    the party identifier
     * @return a {@link Mono} completing when the party is removed
     */
    Mono<Void> removeContractParty(UUID contractId, UUID partyId);

    /**
     * Retrieves all parties for a contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the list of parties
     */
    Mono<List<Object>> getContractParties(UUID contractId);

    /**
     * Adds terms to a contract.
     *
     * @param contractId     the contract identifier
     * @param termTemplateId the term template identifier
     * @param termValue      the term value
     * @return a {@link Mono} emitting the identifier of the created term
     */
    Mono<UUID> addContractTerms(UUID contractId, UUID termTemplateId, String termValue);

    /**
     * Retrieves all terms for a contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the list of terms
     */
    Mono<List<Object>> getContractTerms(UUID contractId);

    /**
     * Retrieves all contracts for a party.
     *
     * @param partyId the party identifier
     * @return a {@link Mono} emitting the list of contracts
     */
    Mono<List<Object>> getContractsByParty(UUID partyId);

    /**
     * Retrieves all active contracts for a party.
     *
     * @param partyId the party identifier
     * @return a {@link Mono} emitting the list of active contracts
     */
    Mono<List<Object>> getActiveContractsByParty(UUID partyId);

    /**
     * Retrieves the current status of a contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the current status
     */
    Mono<Object> getContractStatus(UUID contractId);

    /**
     * Retrieves the full status history of a contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the status history list
     */
    Mono<List<Object>> getContractStatusHistory(UUID contractId);

    /**
     * Generates a document for a contract.
     *
     * @param contractId the contract identifier
     * @param templateId the template identifier
     * @return a {@link Mono} emitting the identifier of the generated document
     */
    Mono<UUID> generateContractDocument(UUID contractId, UUID templateId);

    /**
     * Retrieves all documents for a contract.
     *
     * @param contractId the contract identifier
     * @return a {@link Mono} emitting the list of documents
     */
    Mono<List<Object>> getContractDocuments(UUID contractId);

    /**
     * Retrieves a specific document from a contract.
     *
     * @param contractId the contract identifier
     * @param documentId the document identifier
     * @return a {@link Mono} emitting the document details
     */
    Mono<Object> getContractDocument(UUID contractId, UUID documentId);

    /**
     * Requests a signature on a contract document.
     *
     * @param contractId    the contract identifier
     * @param documentId    the document identifier
     * @param signerPartyId the signing party identifier
     * @return a {@link Mono} emitting the identifier of the created status history entry
     */
    Mono<UUID> requestContractSignature(UUID contractId, UUID documentId, UUID signerPartyId);

    /**
     * Confirms a contract signature.
     *
     * @param contractId         the contract identifier
     * @param signatureRequestId the signature request identifier
     * @return a {@link Mono} completing when the signature is confirmed
     */
    Mono<Void> confirmContractSignature(UUID contractId, String signatureRequestId);
}
