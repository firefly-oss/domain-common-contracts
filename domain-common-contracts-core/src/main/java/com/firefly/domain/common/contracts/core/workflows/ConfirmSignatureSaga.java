package com.firefly.domain.common.contracts.core.workflows;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
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
 * Saga that confirms an e-signature: verifies with the provider, records the APPROVED
 * status, and optionally sends a notification.
 *
 * <p>Step DAG:
 * <pre>
 *   verify-signature       (Layer 0) — checks provider for completed signature
 *          |
 *   update-contract-status (Layer 1) — records APPROVED status on contract
 *          |
 *   send-notification      (Layer 2, best-effort) — notifies relevant parties
 * </pre>
 *
 * <p>Compensation for update-contract-status logs a warning; full status reversion
 * requires business-level decision and is intentionally deferred.
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

    @Autowired
    public ConfirmSignatureSaga(ESignaturePort eSignaturePort,
                                ContractStatusHistoryApi contractStatusHistoryApi) {
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    /**
     * Step 1: Verifies the signature with the configured e-signature provider.
     * Stores the verification status in context.
     */
    @SagaStep(id = "verify-signature")
    public Mono<SignatureVerificationResult> verifySignature(ConfirmContractSignatureCommand cmd,
                                                            ExecutionContext ctx) {
        log.debug("Verifying signature request: {} for contract: {}", cmd.signatureRequestId(), cmd.contractId());
        ctx.putVariable(CTX_CONTRACT_ID, cmd.contractId());

        return eSignaturePort.verifySignature(cmd.signatureRequestId())
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

    /**
     * Compensation for update-contract-status: logs a warning as status reversion
     * requires a business decision (e.g. returning to SUBMITTED_FOR_APPROVAL or DRAFT).
     *
     * <p>TODO: Replace with explicit status reversion once business rules are defined.
     */
    public Mono<Void> revertContractStatus(UUID statusHistoryId, ExecutionContext ctx) {
        UUID contractId = ctx.getVariableAs(CTX_CONTRACT_ID, UUID.class);
        log.warn("Compensation triggered for contract status update on contract {}. " +
                 "Status history {} cannot be automatically reverted — manual intervention required.",
                 contractId, statusHistoryId);
        return Mono.empty();
    }

    /**
     * Step 3 (best-effort): Sends a notification to relevant parties.
     * Silently skips on any error so it does not block the saga from completing.
     *
     * <p>TODO: Replace stub with domain-common-notifications SDK call once dependency is available.
     */
    @SagaStep(id = "send-notification", dependsOn = "update-contract-status")
    public Mono<String> sendNotification() {
        log.debug("Notification step: signature confirmed — skipping (notifications service not wired)");
        // TODO: Replace with domain-common-notifications SDK call once dependency is available
        return Mono.just("skipped");
    }
}
