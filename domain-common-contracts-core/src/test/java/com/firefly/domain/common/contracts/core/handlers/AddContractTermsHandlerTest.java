package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.core.contract.sdk.model.ContractTermDynamicDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
import com.firefly.domain.common.contracts.core.mappers.ContractCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

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
        when(contractTermsApi.createContractTerm(eq(contractId), eq(mappedDto)))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedTermId)
                .verifyComplete();

        verify(mapper).toContractTermDto(cmd);
        verify(contractTermsApi).createContractTerm(contractId, mappedDto);
    }
}
