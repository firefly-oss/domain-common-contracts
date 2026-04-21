package com.firefly.domain.common.contracts.core.workflows;

import com.firefly.commons.ecm.sdk.api.DocumentSignatureControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentSignatureDTO;
import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
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
 * Saga that orchestrates an e-signature request in three atomic steps:
 * record creation → provider submission → contract status update.
 *
 * <p>Step DAG:
 * <pre>
 *   create-signature-request   (Layer 0) — persists signature record in document mgmt
 *            |
 *   send-to-provider           (Layer 1) — submits to e-signature provider
 *            |
 *   update-contract-status     (Layer 2) — records SUBMITTED_FOR_APPROVAL status
 * </pre>
 *
 * <p>Compensation: cancel signature record (best-effort; provider state is not reversed).
 */
@Saga(name = "request-signature-saga")
@Service
@Slf4j
public class RequestSignatureSaga {

    static final String CTX_CONTRACT_ID = "contractId";
    static final String CTX_DOCUMENT_ID = "documentId";
    static final String CTX_SIGNER_PARTY_ID = "signerPartyId";
    static final String CTX_SIGNATURE_RECORD_ID = "signatureRecordId";
    static final String CTX_PROVIDER_REQUEST_ID = "providerRequestId";

    private final DocumentSignatureControllerApi documentSignatureControllerApi;
    private final ESignaturePort eSignaturePort;
    private final ContractStatusHistoryApi contractStatusHistoryApi;

    @Autowired
    public RequestSignatureSaga(DocumentSignatureControllerApi documentSignatureControllerApi,
                                ESignaturePort eSignaturePort,
                                ContractStatusHistoryApi contractStatusHistoryApi) {
        this.documentSignatureControllerApi = documentSignatureControllerApi;
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    /**
     * Step 1: Creates a signature record in the document management system.
     * Stores the signature record ID in context for use by compensation.
     */
    @SagaStep(id = "create-signature-request", compensate = "cancelSignatureRequest")
    public Mono<UUID> createSignatureRequest(RequestContractSignatureCommand cmd, ExecutionContext ctx) {
        log.debug("Creating signature request for document: {}, contract: {}", cmd.documentId(), cmd.contractId());
        // Persist command fields in the context so downstream steps can read them even
        // when the argument resolver does not inject the original command instance.
        ctx.putVariable(CTX_CONTRACT_ID, cmd.contractId());
        ctx.putVariable(CTX_DOCUMENT_ID, cmd.documentId());
        ctx.putVariable(CTX_SIGNER_PARTY_ID, cmd.signerPartyId());

        DocumentSignatureDTO sigDto = new DocumentSignatureDTO()
                .documentId(cmd.documentId())
                .signerPartyId(cmd.signerPartyId());

        return documentSignatureControllerApi.addDocumentSignature(cmd.documentId(), sigDto, UUID.randomUUID().toString())
                .mapNotNull(dto -> Objects.requireNonNull(dto).getId())
                .doOnNext(sigId -> ctx.putVariable(CTX_SIGNATURE_RECORD_ID, sigId));
    }

    /**
     * Compensation for create-signature-request: deletes the signature record.
     */
    public Mono<Void> cancelSignatureRequest(UUID signatureRecordId, ExecutionContext ctx) {
        log.debug("Compensating: cancelling signature record: {}", signatureRecordId);
        UUID documentId = ctx.getVariableAs(CTX_DOCUMENT_ID, UUID.class);
        if (documentId == null) {
            log.warn("Cannot cancel signature record {}: document ID not in context", signatureRecordId);
            return Mono.empty();
        }
        return documentSignatureControllerApi.deleteDocumentSignature(documentId, signatureRecordId, UUID.randomUUID().toString())
                .onErrorComplete(e -> {
                    log.warn("Failed to cancel signature record {}: {}", signatureRecordId, e.getMessage());
                    return true;
                });
    }

    /**
     * Step 2: Submits the signature request to the configured e-signature provider.
     * Depends on create-signature-request.
     */
    @SagaStep(id = "send-to-provider", dependsOn = "create-signature-request")
    public Mono<String> sendToProvider(ExecutionContext ctx) {
        // Read command fields from context: the argument resolver does not reliably
        // inject the original command instance into downstream steps.
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        UUID documentId = ctx.getVariableAs(CTX_DOCUMENT_ID, UUID.class);
        UUID signerPartyId = ctx.getVariableAs(CTX_SIGNER_PARTY_ID, UUID.class);
        log.debug("Sending signature request to provider for contract: {}", contractId);

        SignatureRequest request = SignatureRequest.builder()
                .contractId(contractId)
                .documentId(documentId)
                .signerPartyId(signerPartyId)
                .build();

        return eSignaturePort.requestSignature(request)
                .map(result -> {
                    String providerRequestId = result.getSignatureRequestId();
                    // Saga engine treats Mono.empty() as step failure (map->null becomes empty),
                    // and the execution context does not accept null values. Use a sentinel when
                    // the provider does not return an id (e.g. stub providers).
                    String effectiveId = providerRequestId != null ? providerRequestId : "no-provider-id";
                    ctx.putVariable(CTX_PROVIDER_REQUEST_ID, effectiveId);
                    return effectiveId;
                });
    }

    /**
     * Step 3: Records the SUBMITTED_FOR_APPROVAL status transition on the contract.
     * Depends on send-to-provider.
     */
    @SagaStep(id = "update-contract-status", dependsOn = "send-to-provider")
    public Mono<UUID> updateContractStatus(RequestContractSignatureCommand cmd, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        log.debug("Updating contract status to SUBMITTED_FOR_APPROVAL for contract: {}", contractId);

        ContractStatusHistoryDTO statusDto = new ContractStatusHistoryDTO();
        statusDto.setContractId(contractId);
        statusDto.setStatusCode(ContractStatusHistoryDTO.StatusCodeEnum.SUBMITTED_FOR_APPROVAL);

        return contractStatusHistoryApi.createContractStatusHistory(contractId, statusDto, UUID.randomUUID().toString())
                .mapNotNull(dto -> Objects.requireNonNull(dto).getContractStatusHistoryId());
    }
}
