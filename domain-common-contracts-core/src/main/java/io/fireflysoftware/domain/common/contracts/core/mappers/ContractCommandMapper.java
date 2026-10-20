package io.fireflysoftware.domain.common.contracts.core.mappers;

import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractPartyDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractTermDynamicDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractPartyCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractTermsCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.CreateContractCommand;
import io.fireflysoftware.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContractCommandMapper {

    @Mapping(target = "contractNumber", source = "contractType")
    ContractDTO toContractDto(CreateContractCommand cmd);

    ContractPartyDTO toContractPartyDto(AddContractPartyCommand cmd);

    @Mapping(target = "termValueText", source = "termValue")
    ContractTermDynamicDTO toContractTermDto(AddContractTermsCommand cmd);

    SignatureRequest toSignatureRequest(RequestContractSignatureCommand cmd);
}
