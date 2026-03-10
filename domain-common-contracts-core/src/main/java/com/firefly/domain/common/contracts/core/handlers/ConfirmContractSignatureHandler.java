package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.ConfirmContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link ConfirmContractSignatureCommand} by verifying the signature
 * through the {@link ESignaturePort} and recording the approved status.
 */
@CommandHandlerComponent
public class ConfirmContractSignatureHandler extends CommandHandler<ConfirmContractSignatureCommand, Void> {

    private final ESignaturePort eSignaturePort;
    private final ContractStatusHistoryApi contractStatusHistoryApi;

    public ConfirmContractSignatureHandler(ESignaturePort eSignaturePort,
                                           ContractStatusHistoryApi contractStatusHistoryApi) {
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
    }

    @Override
    protected Mono<Void> doHandle(ConfirmContractSignatureCommand cmd) {
        return eSignaturePort.verifySignature(cmd.signatureRequestId())
                .flatMap(result -> {
                    ContractStatusHistoryDTO statusDto = new ContractStatusHistoryDTO();
                    statusDto.setContractId(cmd.contractId());
                    statusDto.setStatusCode(ContractStatusHistoryDTO.StatusCodeEnum.APPROVED);
                    return contractStatusHistoryApi.createContractStatusHistory(cmd.contractId(), statusDto);
                })
                .then();
    }
}
