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

import io.fireflysoftware.core.contract.sdk.api.ContractTermsApi;
import io.fireflysoftware.core.contract.sdk.model.ContractTermDynamicDTO;
import io.fireflysoftware.domain.common.contracts.core.commands.AddContractTermsCommand;
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
class AddContractTermsHandlerTest {

    @Mock
    private ContractTermsApi contractTermsApi;

    @Mock
    private ContractCommandMapper mapper;

    private AddContractTermsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddContractTermsHandler(contractTermsApi, mapper);
    }

    @Test
    void doHandle_shouldCallCreateContractTermAndReturnId() {
        UUID contractId = UUID.randomUUID();
        UUID termTemplateId = UUID.randomUUID();
        UUID expectedTermId = UUID.randomUUID();
        AddContractTermsCommand cmd = new AddContractTermsCommand(contractId, termTemplateId, "36 months");

        ContractTermDynamicDTO mappedDto = new ContractTermDynamicDTO(null, null, null);
        ContractTermDynamicDTO responseDto = new ContractTermDynamicDTO(expectedTermId, null, null);
        when(mapper.toContractTermDto(cmd)).thenReturn(mappedDto);
        when(contractTermsApi.createContractTerm(eq(contractId), eq(mappedDto), any()))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedTermId)
                .verifyComplete();

        verify(mapper).toContractTermDto(cmd);
        verify(contractTermsApi).createContractTerm(eq(contractId), eq(mappedDto), any());
    }
}
