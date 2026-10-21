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
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ContractCommandMapper {

    @Mapping(target = "contractNumber", source = "contractType")
    ContractDTO toContractDto(CreateContractCommand cmd);

    @Mapping(target = "roleInContractId", source = "role", qualifiedByName = "roleId")
    ContractPartyDTO toContractPartyDto(AddContractPartyCommand cmd);

    @Named("roleId")
    default UUID roleId(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return UUID.fromString(role);
    }

    @Mapping(target = "termValueText", source = "termValue")
    ContractTermDynamicDTO toContractTermDto(AddContractTermsCommand cmd);

    SignatureRequest toSignatureRequest(RequestContractSignatureCommand cmd);
}
