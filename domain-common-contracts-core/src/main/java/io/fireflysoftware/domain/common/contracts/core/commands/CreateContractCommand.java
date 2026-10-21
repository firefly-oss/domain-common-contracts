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
