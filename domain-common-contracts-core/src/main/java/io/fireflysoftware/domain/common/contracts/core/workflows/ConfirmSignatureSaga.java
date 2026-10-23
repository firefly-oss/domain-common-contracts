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

import io.fireflysoftware.core.contract.sdk.api.ContractEventsApi;
import io.fireflysoftware.core.contract.sdk.api.ContractStatusHistoryApi;
import io.fireflysoftware.core.contract.sdk.model.ContractEventDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractStatusHistoryDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga that confirms an e-signature: verifies with the provider, records the APPROVED
 * records the signed contract event.
 *
 * <p>Step DAG:
 * <pre>
 *   verify-signature       (Layer 0) — checks provider for completed signature
 *          |
 *   update-contract-status (Layer 1) — records APPROVED status on contract
 *          |
 *   send-notification      (Layer 2) — records CONTRACT_SIGNED on the contract
 * </pre>
 */
@Saga(name = "confirm-signature-saga")
@Service
@Slf4j
public class ConfirmSignatureSaga {

    static final String CTX_CONTRACT_ID = "contractId";
    static final String CTX_VERIFICATION_STATUS = "verificationStatus";
    static final String CTX_STATUS_HISTORY_ID = "statusHistoryId";

    private final ESignaturePort eSignaturePort;
    private final ContractStatusHistoryApi contractStatusHistoryApi;
    private final ContractEventsApi contractEventsApi;

    @Autowired
    public ConfirmSignatureSaga(ESignaturePort eSignaturePort,
                                ContractStatusHistoryApi contractStatusHistoryApi,
                                ContractEventsApi contractEventsApi) {
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
        this.contractEventsApi = contractEventsApi;
    }

    /**
     * Step 1: Verifies the signature with the configured e-signature provider.
     * Stores the verification status in context.
     */
    @SagaStep(id = "verify-signature")
    public Mono<SignatureVerificationResult> verifySignature(ConfirmContractSignatureCommand cmd,
                                                            ExecutionContext ctx) {
        if (cmd.signatureRequestId() == null || cmd.signatureRequestId().isBlank()) {
            return Mono.error(new IllegalArgumentException("A signature request ID is required"));
        }
        log.debug("Verifying signature request: {} for contract: {}", cmd.signatureRequestId(), cmd.contractId());
        ctx.putVariable(CTX_CONTRACT_ID, cmd.contractId());

        return eSignaturePort.verifySignature(cmd.signatureRequestId())
                .switchIfEmpty(Mono.error(new IllegalStateException("The signature provider returned no verification")))
                .flatMap(result -> {
                    if (!"VERIFIED".equals(result.getStatus())
                            || !Objects.equals(cmd.signatureRequestId(), result.getSignatureRequestId())
                            || result.getProvider() == null || result.getProvider().isBlank()
                            || !Objects.equals(cmd.contractId(), result.getContractId())
                            || result.getContractId() == null || result.getDocumentId() == null || result.getSignerPartyId() == null
                            || result.getTenantId() == null || result.getTenantId().isBlank() || result.getSynthetic() == null) {
                        return Mono.error(new IllegalStateException("The requested signature has not been verified"));
                    }
                    return Mono.just(result);
                })
                .doOnNext(result -> ctx.putVariable(CTX_VERIFICATION_STATUS, result.getStatus()));
    }

    /**
     * Step 2: Records the APPROVED status transition on the contract.
     * Depends on verify-signature completing successfully.
     */
    @SagaStep(id = "update-contract-status", compensate = "revertContractStatus",
              dependsOn = "verify-signature")
    public Mono<UUID> updateContractStatus(ConfirmContractSignatureCommand cmd, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        log.debug("Updating contract status to APPROVED for contract: {}", contractId);

        ContractStatusHistoryDTO statusDto = new ContractStatusHistoryDTO();
        statusDto.setContractId(contractId);
        statusDto.setStatusCode(ContractStatusHistoryDTO.StatusCodeEnum.APPROVED);

        return contractStatusHistoryApi.createContractStatusHistory(contractId, statusDto, UUID.randomUUID().toString())
                .mapNotNull(dto -> Objects.requireNonNull(dto).getContractStatusHistoryId())
                .doOnNext(id -> ctx.putVariable(CTX_STATUS_HISTORY_ID, id));
    }

    public Mono<Void> revertContractStatus(UUID statusHistoryId, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        if (contractId == null || statusHistoryId == null) {
            return Mono.empty();
        }
        return contractStatusHistoryApi
                .deleteContractStatusHistory(contractId, statusHistoryId, UUID.randomUUID().toString())
                .then();
    }

    @SagaStep(id = "send-notification", dependsOn = "update-contract-status", compensate = "removeSignedEvent")
    public Mono<UUID> sendNotification(ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        ContractEventDTO event = new ContractEventDTO();
        event.setContractId(contractId);
        event.setEventType(ContractEventDTO.EventTypeEnum.CONTRACT_SIGNED);
        event.setEventDate(LocalDateTime.now());
        event.setEventDescription("Contract signature confirmed");
        return contractEventsApi.createContractEvent(contractId, event, UUID.randomUUID().toString())
                .mapNotNull(saved -> Objects.requireNonNull(saved).getContractEventId())
                .doOnNext(id -> ctx.putVariable("contractEventId", id));
    }

    public Mono<Void> removeSignedEvent(UUID contractEventId, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        if (contractId == null || contractEventId == null) {
            return Mono.empty();
        }
        return contractEventsApi.deleteContractEvent(contractId, contractEventId, UUID.randomUUID().toString()).then();
    }
}
