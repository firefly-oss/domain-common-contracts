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


package io.fireflysoftware.domain.common.contracts.core.workflows;

import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Saga that orchestrates an e-signature request in three atomic steps:
 * durable core submission → accepted-request check → contract status update.
 *
 * <p>Step DAG:
 * <pre>
 *   create-signature-request   (Layer 0) — submits through the core document signature authority
 *            |
 *   send-to-provider           (Layer 1) — retains the accepted durable request identity
 *            |
 *   update-contract-status     (Layer 2) — records SUBMITTED_FOR_APPROVAL status
 * </pre>
 *
 * <p>Provider requests remain durable when a later domain step fails; retries recover the same request.
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

    private final ESignaturePort eSignaturePort;
    private final ContractStatusHistoryApi contractStatusHistoryApi;

    @Autowired
    public RequestSignatureSaga(ESignaturePort eSignaturePort,
                                ContractStatusHistoryApi contractStatusHistoryApi) {
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    /** Submits once through the core authority, which durably owns request identity and provider state. */
    @SagaStep(id = "create-signature-request")
    public Mono<UUID> createSignatureRequest(RequestContractSignatureCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CONTRACT_ID, cmd.contractId());
        ctx.putVariable(CTX_DOCUMENT_ID, cmd.documentId());
        ctx.putVariable(CTX_SIGNER_PARTY_ID, cmd.signerPartyId());
        var request = SignatureRequest.builder().contractId(cmd.contractId()).documentId(cmd.documentId())
                .signerPartyId(cmd.signerPartyId()).build();
        return eSignaturePort.requestSignature(request)
                .switchIfEmpty(Mono.error(new IllegalStateException("Core returned no signature submission")))
                .flatMap(result -> {
                    if (result.getSignatureRequestId()==null || result.getSignatureRequestId().isBlank()
                            || result.getProvider()==null || result.getProvider().isBlank()
                            || !("PENDING".equals(result.getStatus()) || "COMPLETED".equals(result.getStatus())))
                        return Mono.error(new IllegalStateException("Signature submission is not accepted or has an unknown outcome"));
                    UUID id=UUID.fromString(result.getSignatureRequestId());
                    ctx.putVariable(CTX_SIGNATURE_RECORD_ID,id);
                    ctx.putVariable(CTX_PROVIDER_REQUEST_ID,id.toString());
                    return Mono.just(id);
                });
    }

    /** Provider submission is retained after a later domain failure; deleting it would destroy recovery evidence. */
    @SagaStep(id = "send-to-provider", dependsOn = "create-signature-request")
    public Mono<String> sendToProvider(ExecutionContext ctx) {
        String id=ctx.getVariableAs(CTX_PROVIDER_REQUEST_ID,String.class);
        return id==null || id.isBlank() ? Mono.error(new IllegalStateException("Durable signature request identity is missing")) : Mono.just(id);
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

        return contractStatusHistoryApi.createContractStatusHistory(contractId, statusDto, "signature-submitted:"+ctx.getVariableAs(CTX_PROVIDER_REQUEST_ID,String.class))
                .mapNotNull(dto -> dto.getContractStatusHistoryId());
    }
}
