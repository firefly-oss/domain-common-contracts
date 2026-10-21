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


package io.fireflysoftware.domain.common.contracts.core.commands;

import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractLineDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates an agreement. Currency is a 3-letter ISO code. Lines carry the products
 * on the agreement. A predecessor requires link kind AMENDMENT or RENEWAL.
 */
@Data
@NoArgsConstructor
public class CreateContractCommand implements Command<UUID> {

    private UUID partyId;
    private String contractType;
    private String description;
    private String currencyCode;
    private UUID productId;
    private UUID predecessorContractId;
    private ContractDTO.LinkKindEnum linkKind;
    private List<ContractLineDTO> lines = new ArrayList<>();

    public CreateContractCommand(UUID partyId, String contractType, String description) {
        this.partyId = partyId;
        this.contractType = contractType;
        this.description = description;
    }
}
