package io.fireflysoftware.domain.common.contracts.core.services.impl;

import io.fireflysoftware.domain.common.contracts.core.commands.*;
import io.fireflysoftware.domain.common.contracts.core.queries.*;
import io.fireflysoftware.domain.common.contracts.core.services.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.saga.engine.ExpandEach;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link ContractService} that dispatches
 * commands and queries through the CQRS buses.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final SagaEngine sagaEngine;

    @Override
    public Mono<UUID> createContract(UUID partyId, String contractType, String description) {
        log.debug("Creating contract for party: {}", partyId);
        return createAgreement(new CreateContractCommand(partyId, contractType, description));
    }

    @Override
    public Mono<UUID> createAgreement(CreateContractCommand command) {
        List<io.fireflysoftware.core.contract.sdk.model.ContractLineDTO> lines =
                command.getLines() == null ? List.of() : command.getLines();
        StepInputs inputs = StepInputs.builder()
                .forStepId("createContract", command)
                .forStepId("addContractLine", ExpandEach.of(lines))
                .build();
        return sagaEngine.execute("CreateAgreementSaga", inputs)
                .flatMap(ContractServiceImpl::contractId);
    }

    @Override
    public Mono<UUID> changeStatus(ChangeContractStatusCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("readAgreement", command)
                .forStepId("changeAgreementStatus", command)
                .build();
        return sagaEngine.execute("ChangeAgreementStatusSaga", inputs)
                .flatMap(ContractServiceImpl::contractId);
    }

    @Override
    public Mono<Void> deleteContract(UUID contractId) {
        return commandBus.send(new RemoveContractCommand(contractId));
    }

    private static Mono<UUID> contractId(SagaResult result) {
        if (!result.isSuccess()) {
            Throwable cause = result.error().orElseGet(() -> new IllegalStateException(
                    "Saga " + result.sagaName() + " failed"));
            return Mono.error(cause);
        }
        return Mono.justOrEmpty(result.resultOf("createContract", UUID.class)
                .or(() -> result.resultOf("changeAgreementStatus", UUID.class)))
                .switchIfEmpty(Mono.error(new IllegalStateException("Saga did not return a contract id")));
    }

    @Override
    public Mono<Object> getContractDetail(UUID contractId) {
        log.debug("Retrieving contract detail: {}", contractId);
        return queryBus.query(new GetContractDetailQuery(contractId));
    }

    @Override
    public Mono<UUID> addContractParty(UUID contractId, UUID partyId, String role) {
        log.debug("Adding party {} to contract: {}", partyId, contractId);
        return commandBus.send(new AddContractPartyCommand(contractId, partyId, role));
    }

    @Override
    public Mono<Void> removeContractParty(UUID contractId, UUID partyId) {
        log.debug("Removing party {} from contract: {}", partyId, contractId);
        return commandBus.send(new RemoveContractPartyCommand(contractId, partyId));
    }

    @Override
    public Mono<List<Object>> getContractParties(UUID contractId) {
        log.debug("Retrieving parties for contract: {}", contractId);
        return queryBus.query(new GetContractPartiesQuery(contractId));
    }

    @Override
    public Mono<UUID> addContractTerms(UUID contractId, UUID termTemplateId, String termValue) {
        log.debug("Adding terms to contract: {}", contractId);
        return commandBus.send(new AddContractTermsCommand(contractId, termTemplateId, termValue));
    }

    @Override
    public Mono<List<Object>> getContractTerms(UUID contractId) {
        log.debug("Retrieving terms for contract: {}", contractId);
        return queryBus.query(new GetContractTermsQuery(contractId));
    }

    @Override
    public Mono<List<Object>> getContractsByParty(UUID partyId) {
        log.debug("Retrieving contracts for party: {}", partyId);
        return queryBus.query(new GetContractsByPartyQuery(partyId));
    }

    @Override
    public Mono<List<Object>> getActiveContractsByParty(UUID partyId) {
        log.debug("Retrieving active contracts for party: {}", partyId);
        return queryBus.query(new GetActiveContractsByPartyQuery(partyId));
    }

    @Override
    public Mono<Object> getContractStatus(UUID contractId) {
        log.debug("Retrieving status for contract: {}", contractId);
        return queryBus.query(new GetContractStatusQuery(contractId));
    }

    @Override
    public Mono<List<Object>> getContractStatusHistory(UUID contractId) {
        log.debug("Retrieving status history for contract: {}", contractId);
        return queryBus.query(new GetContractStatusHistoryQuery(contractId));
    }

    @Override
    public Mono<UUID> generateContractDocument(UUID contractId, UUID templateId) {
        log.debug("Generating document for contract: {}", contractId);
        return commandBus.send(new GenerateContractDocumentCommand(contractId, templateId));
    }

    @Override
    public Mono<List<Object>> getContractDocuments(UUID contractId) {
        log.debug("Retrieving documents for contract: {}", contractId);
        return queryBus.query(new GetContractDocumentsQuery(contractId));
    }

    @Override
    public Mono<Object> getContractDocument(UUID contractId, UUID documentId) {
        log.debug("Retrieving document {} for contract: {}", documentId, contractId);
        return queryBus.query(new GetContractDocumentQuery(contractId, documentId));
    }

    @Override
    public Mono<UUID> requestContractSignature(UUID contractId, UUID documentId, UUID signerPartyId) {
        log.debug("Requesting signature for contract: {}, document: {}", contractId, documentId);
        return commandBus.send(new RequestContractSignatureCommand(contractId, documentId, signerPartyId));
    }

    @Override
    public Mono<Void> confirmContractSignature(UUID contractId, String signatureRequestId) {
        log.debug("Confirming signature for contract: {}, request: {}", contractId, signatureRequestId);
        return commandBus.send(new ConfirmContractSignatureCommand(contractId, signatureRequestId));
    }
}
