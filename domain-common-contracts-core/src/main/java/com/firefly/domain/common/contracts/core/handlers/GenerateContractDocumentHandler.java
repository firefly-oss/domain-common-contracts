package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.commons.ecm.sdk.api.DocumentControllerApi;
import com.firefly.commons.ecm.sdk.model.DocumentDTO;
import com.firefly.core.contract.sdk.api.ContractDocumentsApi;
import com.firefly.core.contract.sdk.model.ContractDocumentDTO;
import com.firefly.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@link GenerateContractDocumentCommand} by creating a document in the
 * document management system and then linking it to the contract.
 */
@CommandHandlerComponent
public class GenerateContractDocumentHandler extends CommandHandler<GenerateContractDocumentCommand, UUID> {

    private final DocumentControllerApi documentControllerApi;
    private final ContractDocumentsApi contractDocumentsApi;

    public GenerateContractDocumentHandler(DocumentControllerApi documentControllerApi,
                                           ContractDocumentsApi contractDocumentsApi) {
        this.documentControllerApi = documentControllerApi;
        this.contractDocumentsApi = contractDocumentsApi;
    }

    @Override
    protected Mono<UUID> doHandle(GenerateContractDocumentCommand cmd) {
        DocumentDTO docDto = new DocumentDTO();
        docDto.setName("contract-doc-" + cmd.contractId());
        docDto.setDocumentType(DocumentDTO.DocumentTypeEnum.CONTRACT);

        return documentControllerApi.createDocument(docDto)
                .flatMap(createdDoc -> {
                    ContractDocumentDTO contractDocDto = new ContractDocumentDTO();
                    contractDocDto.setContractId(cmd.contractId());
                    contractDocDto.setDocumentId(Objects.requireNonNull(createdDoc).getId());
                    contractDocDto.setDocumentTypeId(cmd.templateId());
                    return contractDocumentsApi.createContractDocument(cmd.contractId(), contractDocDto);
                })
                .mapNotNull(result -> Objects.requireNonNull(result).getContractDocumentId());
    }
}
