package com.firefly.domain.common.contracts.core.mappers;

import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.core.contract.sdk.model.ContractTermDynamicDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-06T17:23:37+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.1 (Homebrew)"
)
@Component
public class ContractCommandMapperImpl implements ContractCommandMapper {

    @Override
    public ContractDTO toContractDto(CreateContractCommand cmd) {
        if ( cmd == null ) {
            return null;
        }

        ContractDTO contractDTO = new ContractDTO();

        contractDTO.setContractNumber( cmd.contractType() );

        return contractDTO;
    }

    @Override
    public ContractPartyDTO toContractPartyDto(AddContractPartyCommand cmd) {
        if ( cmd == null ) {
            return null;
        }

        ContractPartyDTO contractPartyDTO = new ContractPartyDTO();

        contractPartyDTO.setContractId( cmd.contractId() );
        contractPartyDTO.setPartyId( cmd.partyId() );

        return contractPartyDTO;
    }

    @Override
    public ContractTermDynamicDTO toContractTermDto(AddContractTermsCommand cmd) {
        if ( cmd == null ) {
            return null;
        }

        ContractTermDynamicDTO contractTermDynamicDTO = new ContractTermDynamicDTO();

        contractTermDynamicDTO.setTermValueText( cmd.termValue() );
        contractTermDynamicDTO.setContractId( cmd.contractId() );
        contractTermDynamicDTO.setTermTemplateId( cmd.termTemplateId() );

        return contractTermDynamicDTO;
    }

    @Override
    public SignatureRequest toSignatureRequest(RequestContractSignatureCommand cmd) {
        if ( cmd == null ) {
            return null;
        }

        SignatureRequest.SignatureRequestBuilder signatureRequest = SignatureRequest.builder();

        signatureRequest.contractId( cmd.contractId() );
        signatureRequest.documentId( cmd.documentId() );
        signatureRequest.signerPartyId( cmd.signerPartyId() );

        return signatureRequest.build();
    }
}
