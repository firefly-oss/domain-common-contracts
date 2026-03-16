package com.firefly.domain.common.contracts.core.workflows;

import com.firefly.commons.ecm.sdk.api.DocumentControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentDTO;
import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.model.ContractDocumentDTO;
import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Saga that orchestrates document generation and attachment to a contract atomically.
 *
 * <p>Step DAG:
 * <pre>
 *   generate-document      (Layer 0) — creates document in ECM
 *        |
 *   attach-to-contract     (Layer 1) — links document to contract record
 * </pre>
 *
 * <p>Compensation: detach-from-contract → delete-document (reverse order).
 */
@Saga(name = "generate-document-saga")
@Service
@Slf4j
public class GenerateDocumentSaga {

    static final String CTX_DOCUMENT_ID = "documentId";
    static final String CTX_CONTRACT_ID = "contractId";
    static final String CTX_CONTRACT_DOC_ID = "contractDocumentId";

    private final DocumentControllerApi documentControllerApi;
    private final ContractDocumentsApi contractDocumentsApi;

    @Autowired
    public GenerateDocumentSaga(DocumentControllerApi documentControllerApi,
                                ContractDocumentsApi contractDocumentsApi) {
        this.documentControllerApi = documentControllerApi;
        this.contractDocumentsApi = contractDocumentsApi;
    }

    /**
     * Step 1: Creates a document in the ECM system.
     * Stores the generated document ID in context for use by subsequent steps.
     */
    @SagaStep(id = "generate-document", compensate = "deleteDocument")
    public Mono<UUID> generateDocument(GenerateContractDocumentCommand cmd, ExecutionContext ctx) {
        log.debug("Generating document for contract: {}", cmd.contractId());
        ctx.putVariable(CTX_CONTRACT_ID, cmd.contractId());

        DocumentDTO docDto = new DocumentDTO();
        docDto.setName("contract-doc-" + cmd.contractId());
        docDto.setDocumentType(DocumentDTO.DocumentTypeEnum.CONTRACT);

        return documentControllerApi.createDocument(docDto, UUID.randomUUID().toString())
                .mapNotNull(DocumentDTO::getId)
                .doOnNext(docId -> ctx.putVariable(CTX_DOCUMENT_ID, docId));
    }

    /**
     * Compensation for generate-document: deletes the generated document from ECM.
     */
    public Mono<Void> deleteDocument(UUID documentId) {
        log.debug("Compensating: deleting document: {}", documentId);
        return documentControllerApi.deleteDocument(documentId, UUID.randomUUID().toString());
    }

    /**
     * Step 2: Links the generated document to the contract record.
     * Depends on generate-document; reads the document ID from execution context.
     */
    @SagaStep(id = "attach-to-contract", compensate = "detachFromContract", dependsOn = "generate-document")
    public Mono<UUID> attachToContract(GenerateContractDocumentCommand cmd, ExecutionContext ctx) {
        UUID documentId = ctx.getVariableAs(CTX_DOCUMENT_ID, UUID.class);
        log.debug("Attaching document {} to contract: {}", documentId, cmd.contractId());

        ContractDocumentDTO contractDocDto = new ContractDocumentDTO();
        contractDocDto.setContractId(cmd.contractId());
        contractDocDto.setDocumentId(documentId);
        contractDocDto.setDocumentTypeId(cmd.templateId());

        return contractDocumentsApi.createContractDocument(cmd.contractId(), contractDocDto, UUID.randomUUID().toString())
                .mapNotNull(dto -> Objects.requireNonNull(dto).getContractDocumentId())
                .doOnNext(id -> ctx.putVariable(CTX_CONTRACT_DOC_ID, id));
    }

    /**
     * Compensation for attach-to-contract: removes the contract-document link record.
     */
    public Mono<Void> detachFromContract(UUID contractDocumentId, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        log.debug("Compensating: detaching document {} from contract: {}", contractDocumentId, contractId);
        return contractDocumentsApi.deleteContractDocument(contractId, contractDocumentId, UUID.randomUUID().toString());
    }
}
