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


package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.model.ContractPartyDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractPartyCommand;
import io.fireflysoftware.domain.common.contracts.core.mappers.ContractCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddContractPartyHandlerTest {

    @Mock
    private ContractPartiesApi contractPartiesApi;

    @Mock
    private ContractCommandMapper mapper;

    private AddContractPartyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddContractPartyHandler(contractPartiesApi, mapper);
    }

    @Test
    void doHandle_shouldCallCreateContractPartyAndReturnId() {
        UUID contractId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID expectedPartyId = UUID.randomUUID();
        AddContractPartyCommand cmd = new AddContractPartyCommand(contractId, partyId, "BORROWER");

        ContractPartyDTO mappedDto = new ContractPartyDTO(null, null, null);
        ContractPartyDTO responseDto = new ContractPartyDTO(expectedPartyId, null, null);
        when(mapper.toContractPartyDto(cmd)).thenReturn(mappedDto);
        when(contractPartiesApi.createContractParty(eq(contractId), eq(mappedDto), any()))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedPartyId)
                .verifyComplete();

        verify(mapper).toContractPartyDto(cmd);
        verify(contractPartiesApi).createContractParty(eq(contractId), eq(mappedDto), any());
    }
}
