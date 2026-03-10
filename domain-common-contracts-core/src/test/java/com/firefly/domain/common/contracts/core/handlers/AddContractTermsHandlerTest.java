package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.ContractTermsApi;
import com.firefly.core.contract.sdk.model.ContractTermDynamicDTO;
import com.firefly.domain.common.contracts.core.commands.AddContractTermsCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddContractTermsHandlerTest {

    @Mock
    private ContractTermsApi contractTermsApi;

    private AddContractTermsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddContractTermsHandler(contractTermsApi);
    }

    @Test
    void doHandle_shouldCallCreateContractTermAndReturnId() {
        UUID contractId = UUID.randomUUID();
        UUID termTemplateId = UUID.randomUUID();
        UUID expectedTermId = UUID.randomUUID();
        AddContractTermsCommand cmd = new AddContractTermsCommand(contractId, termTemplateId, "36 months");

        ContractTermDynamicDTO responseDto = new ContractTermDynamicDTO(expectedTermId, null, null);
        when(contractTermsApi.createContractTerm(eq(contractId), any(ContractTermDynamicDTO.class)))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(handler.doHandle(cmd))
                .expectNext(expectedTermId)
                .verifyComplete();

        ArgumentCaptor<ContractTermDynamicDTO> captor = ArgumentCaptor.forClass(ContractTermDynamicDTO.class);
        verify(contractTermsApi).createContractTerm(eq(contractId), captor.capture());
        assertThat(captor.getValue().getTermTemplateId()).isEqualTo(termTemplateId);
        assertThat(captor.getValue().getTermValueText()).isEqualTo("36 months");
        assertThat(captor.getValue().getContractId()).isEqualTo(contractId);
    }
}
