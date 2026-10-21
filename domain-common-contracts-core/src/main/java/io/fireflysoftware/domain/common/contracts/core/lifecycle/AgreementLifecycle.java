package io.fireflysoftware.domain.common.contracts.core.lifecycle;

import io.fireflysoftware.core.contract.sdk.model.ContractDTO;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The same moves the contract core allows. Terminal agreements stay on file.
 */
public final class AgreementLifecycle {

    private static final Map<ContractDTO.ContractStatusEnum, Set<ContractDTO.ContractStatusEnum>> ALLOWED = Map.of(
            ContractDTO.ContractStatusEnum.DRAFT, EnumSet.of(
                    ContractDTO.ContractStatusEnum.PENDING_APPROVAL,
                    ContractDTO.ContractStatusEnum.ACTIVE,
                    ContractDTO.ContractStatusEnum.CANCELLED),
            ContractDTO.ContractStatusEnum.PENDING_APPROVAL, EnumSet.of(
                    ContractDTO.ContractStatusEnum.DRAFT,
                    ContractDTO.ContractStatusEnum.ACTIVE,
                    ContractDTO.ContractStatusEnum.CANCELLED),
            ContractDTO.ContractStatusEnum.ACTIVE, EnumSet.of(
                    ContractDTO.ContractStatusEnum.SUSPENDED,
                    ContractDTO.ContractStatusEnum.TERMINATED,
                    ContractDTO.ContractStatusEnum.EXPIRED),
            ContractDTO.ContractStatusEnum.SUSPENDED, EnumSet.of(
                    ContractDTO.ContractStatusEnum.ACTIVE,
                    ContractDTO.ContractStatusEnum.TERMINATED)
    );

    private AgreementLifecycle() {
    }

    public static boolean canTransition(ContractDTO.ContractStatusEnum from, ContractDTO.ContractStatusEnum to) {
        if (from == to) {
            return true;
        }
        Set<ContractDTO.ContractStatusEnum> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }

    public static boolean canDelete(ContractDTO.ContractStatusEnum status) {
        return status == null || status == ContractDTO.ContractStatusEnum.DRAFT;
    }
}
