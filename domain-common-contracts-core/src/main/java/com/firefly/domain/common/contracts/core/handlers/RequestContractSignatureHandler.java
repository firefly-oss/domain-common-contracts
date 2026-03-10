package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractStatusHistoryApi;
import com.firefly.core.contract.sdk.model.ContractStatusHistoryDTO;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.core.mappers.ContractCommandMapper;
import com.firefly.domain.common.contracts.infra.ports.ESignaturePort;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@link RequestContractSignatureCommand} by requesting an e-signature
 * through the {@link ESignaturePort} and recording the status transition.
 */
@CommandHandlerComponent
public class RequestContractSignatureHandler extends CommandHandler<RequestContractSignatureCommand, UUID> {

    private final ESignaturePort eSignaturePort;
    private final ContractStatusHistoryApi contractStatusHistoryApi;
    private final ContractCommandMapper mapper;

    public RequestContractSignatureHandler(ESignaturePort eSignaturePort,
                                           ContractStatusHistoryApi contractStatusHistoryApi,
                                           ContractCommandMapper mapper) {
        this.eSignaturePort = eSignaturePort;
        this.contractStatusHistoryApi = contractStatusHistoryApi;
        this.mapper = mapper;
    }

    @Override
    protected Mono<UUID> doHandle(RequestContractSignatureCommand cmd) {
        return eSignaturePort.requestSignature(mapper.toSignatureRequest(cmd))
                .flatMap(result -> {
                    ContractStatusHistoryDTO statusDto = new ContractStatusHistoryDTO();
                    statusDto.setContractId(cmd.contractId());
                    statusDto.setStatusCode(ContractStatusHistoryDTO.StatusCodeEnum.SUBMITTED_FOR_APPROVAL);
                    return contractStatusHistoryApi.createContractStatusHistory(cmd.contractId(), statusDto)
                            .mapNotNull(sh -> Objects.requireNonNull(sh).getContractStatusHistoryId());
                });
    }
}
