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
