package com.firefly.domain.common.contracts.core.mappers;

import com.firefly.core.contract.sdk.model.ContractDTO;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.core.contract.sdk.model.ContractTermDynamicDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractPartyCommand;
import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
import com.firefly.domain.common.contracts.core.commands.CreateContractCommand;
import com.firefly.domain.common.contracts.core.commands.RequestContractSignatureCommand;
import com.firefly.domain.common.contracts.infra.ports.model.SignatureRequest;
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
